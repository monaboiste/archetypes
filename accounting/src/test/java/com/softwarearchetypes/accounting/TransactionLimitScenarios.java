package com.softwarearchetypes.accounting;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.accounting.TransactionEntriesConstraint.BALANCING_CONSTRAINT;
import static com.softwarearchetypes.quantity.money.Money.pln;
import static java.time.Clock.fixed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionLimitScenarios {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 8);
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant AT = DAY.atTime(10, 0).atZone(ZONE).toInstant();
    private final AccountingFacade accounting = AccountingConfiguration.inMemory(fixed(NOW, ZONE)).facade();
    private final Map<String, AccountId> accounts = new HashMap<>();
    private final AccountId funding = createAccount("BANK:FUNDING", "ASSET");
    private final AccountId grants = createAccount("BANK:LIMIT_GRANTS", "OFF_BALANCE");
    private final AccountId merchant = createAccount("MERCHANT:456:SETTLEMENT", "LIABILITY");

    @BeforeEach
    void provisionCustomerAndAllowances() {
        provisionCustomer("123");
        openPeriods("123", DAY, 500, 800);
    }

    @Test
    void authorization_records_money_and_both_allowances_in_one_transaction() {
        Transaction hold = authorize("123", pln(150), AT, AT);

        assertThat(accounting.balance(wallet("123"))).hasValue(pln(4850));
        assertThat(accounting.balance(held("123"))).hasValue(pln(150));
        assertPeriod("123", "DAILY", DAY, 350, 150);
        assertPeriod("123", "MONTHLY", DAY, 650, 150);
        List<EntryView> entries = entriesOf(hold.id());
        assertThat(entries).hasSize(6).allSatisfy(entry -> {
            assertThat(entry.transactionId()).isEqualTo(hold.id());
            assertThat(entry.metadata().metadata()).containsEntry("customerId", "123")
                    .containsEntry("authorizationId", hold.id().value().toString());
        });
        assertThat(entries.stream().map(EntryView::amount).reduce(pln(0), Money::add)).isEqualTo(pln(0));
        assertThat(usage("123", "DAILY", window("DAILY", DAY)).balance()).isEqualTo(pln(150));
        assertThat(usage("123", "MONTHLY", window("MONTHLY", DAY)).balance()).isEqualTo(pln(150));
    }

    @Test
    void multiple_authorizations_accumulate_usage_and_projection_retains_original_facts() {
        Transaction first = authorize("123", pln(120), AT, AT);
        Transaction second = authorize("123", pln(80), AT.plusSeconds(60), AT.plusSeconds(60));

        AccountView projection = usage("123", "DAILY", window("DAILY", DAY));

        assertThat(projection.balance()).isEqualTo(pln(200));
        assertThat(projection.entries()).hasSize(2)
                .extracting(EntryView::transactionId).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(projection.entries()).allSatisfy(entry ->
                assertThat(accounting.findAccount(entry.accountId()).orElseThrow().entries()).contains(entry));
        assertPeriod("123", "DAILY", DAY, 300, 200);
        assertPeriod("123", "MONTHLY", DAY, 600, 200);
    }

    @Test
    void daily_allowance_can_be_fully_used_but_not_exceeded() {
        authorize("123", pln(500), AT, AT);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> authorization("123", pln(1), AT, AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negative balance");

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
        assertPeriod("123", "DAILY", DAY, 0, 500);
        assertPeriod("123", "MONTHLY", DAY, 300, 500);
    }

    @Test
    void monthly_allowance_accumulates_across_days_and_can_block_a_funded_daily_allowance() {
        authorize("123", pln(500), AT, AT);
        LocalDate tomorrow = DAY.plusDays(1);
        openPeriods("123", tomorrow, 500, 800);
        Instant next = tomorrow.atTime(10, 0).atZone(ZONE).toInstant();
        authorize("123", pln(300), next, next);
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> authorization("123", pln(1), next, next))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negative balance");

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
        assertPeriod("123", "DAILY", tomorrow, 200, 300);
        assertPeriod("123", "MONTHLY", tomorrow, 0, 800);
    }

    @Test
    void prebuilt_authorization_rechecks_both_allowances_before_posting_money() {
        Transaction first = authorization("123", pln(300), AT, AT);
        Transaction stale = authorization("123", pln(300), AT, AT);
        assertThat(accounting.execute(first).success()).isTrue();
        List<AccountView> before = accounting.findAll();

        assertThat(accounting.execute(stale).failure()).isTrue();

        assertThat(accounting.findTransactionBy(stale.id())).isEmpty();
        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(accounting.balance(held("123"))).hasValue(pln(300));
    }

    @Test
    void balance_constraint_counts_all_proposed_debits_on_an_allowance() {
        AccountId available = allowance("123", "DAILY", DAY, "AVAILABLE");
        List<AccountView> before = accounting.findAll();

        assertThatThrownBy(() -> accounting.transaction().occurredAt(AT).appliesAt(AT).withTypeOf("LIMIT_USED")
                .withTransactionEntriesConstraint(BALANCING_CONSTRAINT.and(new NonNegativeBalanceConstraint(Set.of(available))))
                .executing().debitFrom(available, pln(280)).debitFrom(available, pln(280))
                .creditTo(allowance("123", "DAILY", DAY, "USED"), pln(560)).build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void balance_constraint_validates_the_net_change_not_each_debit_in_isolation() {
        AccountId available = allowance("123", "DAILY", DAY, "AVAILABLE");
        Transaction netUsage = accounting.transaction().occurredAt(AT).appliesAt(AT).withTypeOf("LIMIT_ADJUSTED")
                .withTransactionEntriesConstraint(BALANCING_CONSTRAINT.and(new NonNegativeBalanceConstraint(Set.of(available))))
                .executing().debitFrom(available, pln(600)).creditTo(available, pln(100))
                .creditTo(allowance("123", "DAILY", DAY, "USED"), pln(500)).build();

        assertThat(accounting.execute(netUsage).success()).isTrue();

        assertPeriod("123", "DAILY", DAY, 0, 500);
        assertPeriod("123", "MONTHLY", DAY, 800, 0);
    }

    @Test
    void settlement_does_not_consume_allowances_a_second_time() {
        Transaction hold = authorize("123", pln(200), AT, AT);

        settle(hold.id(), pln(150), AT.plusSeconds(60));

        assertPeriod("123", "DAILY", DAY, 300, 200);
        assertPeriod("123", "MONTHLY", DAY, 600, 200);
        assertThat(accounting.balance(held("123"))).hasValue(pln(50));
        assertThat(accounting.balance(merchant)).hasValue(pln(150));
    }

    @Test
    void cancellation_returns_only_unused_commitment_and_allows_its_reuse() {
        Transaction hold = authorize("123", pln(200), AT, AT);
        settle(hold.id(), pln(50), AT.plusSeconds(60));

        cancel(hold.id(), AT.plusSeconds(120));

        assertPeriod("123", "DAILY", DAY, 450, 50);
        assertPeriod("123", "MONTHLY", DAY, 750, 50);
        authorize("123", pln(450), AT.plusSeconds(180), AT.plusSeconds(180));
        assertPeriod("123", "DAILY", DAY, 0, 500);
        assertPeriod("123", "MONTHLY", DAY, 300, 500);
        assertThat(accounting.balance(merchant)).hasValue(pln(50));
        assertThat(usage("123", "DAILY", window("DAILY", DAY)).balance()).isEqualTo(pln(500));
    }

    @Test
    void daily_renewal_keeps_monthly_usage_and_does_not_overwrite_yesterdays_ledger() {
        authorize("123", pln(400), AT, AT);
        AccountView yesterday = accounting.findAccount(allowance("123", "DAILY", DAY, "USED")).orElseThrow();
        LocalDate tomorrow = DAY.plusDays(1);

        openPeriods("123", tomorrow, 500, 800);
        Instant next = tomorrow.atTime(10, 0).atZone(ZONE).toInstant();
        authorize("123", pln(100), next, next);

        assertPeriod("123", "DAILY", tomorrow, 400, 100);
        assertPeriod("123", "MONTHLY", tomorrow, 300, 500);
        assertThat(accounting.findAccount(yesterday.id())).contains(yesterday);
        assertThat(usage("123", "DAILY", window("DAILY", DAY)).balance()).isEqualTo(pln(400));
        assertThat(usage("123", "DAILY", window("DAILY", tomorrow)).balance()).isEqualTo(pln(100));
    }

    @Test
    void month_renewal_grants_new_capacity_without_erasing_last_months_usage() {
        authorize("123", pln(400), AT, AT);
        LocalDate nextMonth = DAY.withDayOfMonth(1).plusMonths(1);

        openPeriods("123", nextMonth, 500, 800);

        assertPeriod("123", "MONTHLY", nextMonth, 800, 0);
        assertPeriod("123", "MONTHLY", DAY, 400, 400);
        assertThat(usage("123", "MONTHLY", window("MONTHLY", DAY)).balance()).isEqualTo(pln(400));
        assertThat(usage("123", "MONTHLY", window("MONTHLY", nextMonth)).balance()).isEqualTo(pln(0));
    }

    @Test
    void late_cancellation_returns_allowance_to_original_periods_not_the_current_period() {
        Transaction oldHold = authorize("123", pln(200), AT, AT);
        LocalDate nextMonth = DAY.withDayOfMonth(1).plusMonths(1);
        openPeriods("123", nextMonth, 500, 800);
        Instant next = nextMonth.atTime(10, 0).atZone(ZONE).toInstant();
        authorize("123", pln(100), next, next);

        cancel(oldHold.id(), next.plusSeconds(60));

        assertPeriod("123", "DAILY", DAY, 500, 0);
        assertPeriod("123", "MONTHLY", DAY, 800, 0);
        assertPeriod("123", "DAILY", nextMonth, 400, 100);
        assertPeriod("123", "MONTHLY", nextMonth, 700, 100);
        AccountId oldUsage = allowance("123", "MONTHLY", DAY, "USED");
        assertThat(accounting.balanceAsOf(oldUsage, next)).hasValue(pln(200));
        assertThat(accounting.balanceAsOf(oldUsage, next.plusSeconds(60))).hasValue(pln(0));
        // Posting-time selection differs from the old period's corrected balance as of the report time.
        assertThat(usage("123", "MONTHLY", window("MONTHLY", DAY)).balance()).isEqualTo(pln(200));
        assertThat(usage("123", "MONTHLY", window("MONTHLY", nextMonth)).balance()).isEqualTo(pln(-100));
        assertThat(periodUsage("123", "MONTHLY", DAY, next.plusSeconds(60)).balance()).isEqualTo(pln(0));
        assertThat(periodUsage("123", "MONTHLY", nextMonth, next.plusSeconds(60)).balance()).isEqualTo(pln(100));
    }

    @Test
    void projection_selects_only_the_requested_customer_and_limit_kind() {
        provisionCustomer("789");
        openPeriods("789", DAY, 500, 800);
        authorize("123", pln(100), AT, AT);
        authorize("789", pln(250), AT, AT);

        assertThat(usage("123", "DAILY", window("DAILY", DAY)).balance()).isEqualTo(pln(100));
        assertThat(usage("789", "DAILY", window("DAILY", DAY)).balance()).isEqualTo(pln(250));
        assertThat(usage("123", "MONTHLY", window("MONTHLY", DAY)).balance()).isEqualTo(pln(100));
    }

    @Test
    void utilization_uses_effective_time_not_occurrence_time() {
        Instant before = window("DAILY", DAY).validFrom().minusNanos(1);
        authorize("123", pln(100), before, AT);

        assertThat(usage("123", "DAILY", window("DAILY", DAY)).balance()).isEqualTo(pln(100));
        AccountEntryFilter occurredToday = usedAccounts("123", "DAILY").onDate(window("DAILY", DAY)::isValidAt);
        assertThat(project(occurredToday).balance()).isEqualTo(pln(0));
    }

    @Test
    void reporting_window_includes_start_and_excludes_end() {
        LocalDate yesterday = DAY.minusDays(1);
        LocalDate tomorrow = DAY.plusDays(1);
        openPeriods("123", yesterday, 500, 800);
        openPeriods("123", tomorrow, 500, 800);
        Validity day = window("DAILY", DAY);
        authorize("123", pln(10), day.validFrom().minusNanos(1), day.validFrom().minusNanos(1));
        authorize("123", pln(20), day.validFrom(), day.validFrom());
        authorize("123", pln(30), day.validTo(), day.validTo());

        AccountView report = usage("123", "DAILY", day);

        assertThat(report.balance()).isEqualTo(pln(20));
        assertThat(report.entries()).singleElement().satisfies(entry -> assertThat(entry.appliesAt()).isEqualTo(day.validFrom()));
    }

    @Test
    void spring_dst_daily_window_has_23_hours_not_24() {
        assertDstWindow(LocalDate.of(2026, 3, 29), 23);
    }

    @Test
    void autumn_dst_daily_window_has_25_hours_not_24() {
        assertDstWindow(LocalDate.of(2026, 10, 25), 25);
    }

    private void assertDstWindow(LocalDate date, int hours) {
        openPeriods("123", date, 500, 800);
        openPeriods("123", date.plusDays(1), 500, 800);
        Validity day = window("DAILY", date);
        Instant lastHalfHour = day.validTo().minusSeconds(1800);
        authorize("123", pln(100), lastHalfHour, lastHalfHour);
        authorize("123", pln(50), day.validTo(), day.validTo());

        assertThat(Duration.between(day.validFrom(), day.validTo())).isEqualTo(Duration.ofHours(hours));
        assertThat(usage("123", "DAILY", day).balance()).isEqualTo(pln(100));
    }

    private Transaction authorize(String customer, Money amount, Instant occurredAt, Instant appliesAt) {
        Transaction transaction = authorization(customer, amount, occurredAt, appliesAt);
        assertThat(accounting.execute(transaction).success()).isTrue();
        return transaction;
    }

    private Transaction authorization(String customer, Money amount, Instant occurredAt, Instant appliesAt) {
        LocalDate date = appliesAt.atZone(ZONE).toLocalDate();
        AccountId dailyAvailable = allowance(customer, "DAILY", date, "AVAILABLE");
        AccountId monthlyAvailable = allowance(customer, "MONTHLY", date, "AVAILABLE");
        TransactionId id = TransactionId.generate();
        // Command + Specification (L04/L09): check both allowances before the single six-entry posting.
        return accounting.transaction().id(id).occurredAt(occurredAt).appliesAt(appliesAt).withTypeOf("HOLD_PLACED")
                .withMetadata("customerId", customer, "authorizationId", id.value().toString(), "reason", "Card authorization")
                .withTransactionEntriesConstraint(BALANCING_CONSTRAINT.and(
                        new NonNegativeBalanceConstraint(Set.of(dailyAvailable, monthlyAvailable))))
                .executing()
                .debitFrom(wallet(customer), amount).creditTo(held(customer), amount)
                .debitFrom(dailyAvailable, amount).creditTo(allowance(customer, "DAILY", date, "USED"), amount)
                .debitFrom(monthlyAvailable, amount).creditTo(allowance(customer, "MONTHLY", date, "USED"), amount)
                .build();
    }

    private void settle(TransactionId holdId, Money amount, Instant at) {
        EntryView source = holdCredit(holdId);
        Transaction settlement = accounting.transaction().occurredAt(at).appliesAt(at).withTypeOf("HOLD_SETTLED")
                .withMetadata("holdTransactionId", holdId.value().toString())
                .executing().debitFrom(source.accountId(), amount, source.entryId()).creditTo(merchant, amount).build();
        assertThat(accounting.execute(settlement).success()).isTrue();
    }

    private void cancel(TransactionId holdId, Instant at) {
        EntryView source = holdCredit(holdId);
        String customer = source.metadata().metadata().get("customerId");
        Money remainder = accounting.findAccount(source.accountId()).orElseThrow().entries().stream()
                .filter(entry -> entry.appliedTo().filter(source.entryId()::equals).isPresent())
                .map(EntryView::amount).reduce(source.amount(), Money::add);
        var builder = accounting.transaction().occurredAt(at).appliesAt(at).withTypeOf("HOLD_RELEASED")
                .withMetadata("customerId", customer, "holdTransactionId", holdId.value().toString(), "reason", "Cancellation")
                .executing().debitFrom(source.accountId(), remainder, source.entryId()).creditTo(wallet(customer), remainder);
        // Allocation (L06): refund original usage credits; never choose today's period for a late cancellation.
        for (EntryView usage : entriesOf(holdId)) {
            String name = accounting.findAccount(usage.accountId()).orElseThrow().name();
            if (name.endsWith(":USED")) {
                AccountId available = accounts.get(name.substring(0, name.length() - "USED".length()) + "AVAILABLE");
                builder.debitFrom(usage.accountId(), remainder, usage.entryId()).creditTo(available, remainder);
            }
        }
        assertThat(accounting.execute(builder.build()).success()).isTrue();
    }

    private EntryView holdCredit(TransactionId holdId) {
        return entriesOf(holdId).stream().filter(entry ->
                accounting.findAccount(entry.accountId()).orElseThrow().name().endsWith(":HELD"))
                .findFirst().orElseThrow();
    }

    private void openPeriods(String customer, LocalDate date, int daily, int monthly) {
        openPeriod(customer, "DAILY", date, daily);
        openPeriod(customer, "MONTHLY", date, monthly);
    }

    private void openPeriod(String customer, String kind, LocalDate date, int amount) {
        String prefix = periodPrefix(customer, kind, date);
        if (accounts.containsKey(prefix + "AVAILABLE")) {
            return;
        }
        // Semantic memo Accounts (L07/L08): allowances are information, not a second copy of customer money.
        AccountId available = createAccount(prefix + "AVAILABLE", "OFF_BALANCE");
        createAccount(prefix + "USED", "OFF_BALANCE");
        Validity period = window(kind, date);
        Transaction grant = accounting.transaction().occurredAt(period.validFrom()).appliesAt(period.validFrom())
                .withTypeOf("LIMIT_GRANTED").withMetadata("customerId", customer, "limitKind", kind, "period", prefix)
                .executing().debitFrom(grants, pln(amount)).creditTo(available, pln(amount), period).build();
        assertThat(accounting.execute(grant).success()).isTrue();
    }

    private AccountView usage(String customer, String kind, Validity window) {
        // Projection (L08): select original usage facts by account role and effective-time window.
        return project(usedAccounts(customer, kind).onAppliesAt(window::isValidAt));
    }

    private AccountView periodUsage(String customer, String kind, LocalDate period, Instant asOf) {
        return project(AccountEntryFilter.filtering()
                .onAccountEquals(allowance(customer, kind, period, "USED"))
                .onAppliesAt(time -> !time.isAfter(asOf)));
    }

    private AccountEntryFilter usedAccounts(String customer, String kind) {
        return AccountEntryFilter.filtering().onAccountDescriptionContaining("CUSTOMER:" + customer + ":" + kind + ":")
                .onAccountDescriptionContaining(":USED");
    }

    private AccountView project(AccountEntryFilter filter) {
        AccountId projection = AccountId.generate();
        assertThat(accounting.createProjectingAccount(projection, filter, "Limit utilization").success()).isTrue();
        return accounting.findAccount(projection).orElseThrow();
    }

    private Validity window(String kind, LocalDate date) {
        LocalDate start = kind.equals("DAILY") ? date : date.withDayOfMonth(1);
        LocalDate end = kind.equals("DAILY") ? start.plusDays(1) : start.plusMonths(1);
        return Validity.between(start.atStartOfDay(ZONE).toInstant(), end.atStartOfDay(ZONE).toInstant());
    }

    private String periodPrefix(String customer, String kind, LocalDate date) {
        String period = kind.equals("DAILY") ? date.toString() : YearMonth.from(date).toString();
        return "CUSTOMER:" + customer + ":" + kind + ":" + period + ":";
    }

    private AccountId allowance(String customer, String kind, LocalDate date, String role) {
        return accounts.get(periodPrefix(customer, kind, date) + role);
    }

    private void assertPeriod(String customer, String kind, LocalDate date, int available, int used) {
        assertThat(accounting.balance(allowance(customer, kind, date, "AVAILABLE"))).hasValue(pln(available));
        assertThat(accounting.balance(allowance(customer, kind, date, "USED"))).hasValue(pln(used));
    }

    private void provisionCustomer(String customer) {
        createAccount("CUSTOMER:" + customer + ":WALLET", "LIABILITY");
        createAccount("CUSTOMER:" + customer + ":HELD", "LIABILITY");
        Instant fundedAt = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(accounting.transfer(funding, wallet(customer), pln(5000), fundedAt, fundedAt).success()).isTrue();
    }

    private AccountId wallet(String customer) {
        return accounts.get("CUSTOMER:" + customer + ":WALLET");
    }

    private AccountId held(String customer) {
        return accounts.get("CUSTOMER:" + customer + ":HELD");
    }

    private List<EntryView> entriesOf(TransactionId id) {
        return accounting.findTransactionBy(id).orElseThrow().entries().stream()
                .flatMap(accountEntries -> accountEntries.entries().stream()).toList();
    }

    private AccountId createAccount(String name, String type) {
        var result = accounting.createAccount(CreateAccount.generate(name, type));
        assertThat(result.success()).isTrue();
        accounts.put(name, result.getSuccess());
        return result.getSuccess();
    }
}
