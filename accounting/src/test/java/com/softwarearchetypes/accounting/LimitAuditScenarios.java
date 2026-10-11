package com.softwarearchetypes.accounting;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.softwarearchetypes.accounting.postingrules.AccountFinder;
import com.softwarearchetypes.accounting.postingrules.EligibilityCondition;
import com.softwarearchetypes.accounting.postingrules.PostingContext;
import com.softwarearchetypes.accounting.postingrules.PostingRule;
import com.softwarearchetypes.accounting.postingrules.PostingRuleBuilder;
import com.softwarearchetypes.accounting.postingrules.PostingRuleId;
import com.softwarearchetypes.accounting.postingrules.PostingRulesConfiguration;
import com.softwarearchetypes.accounting.postingrules.PostingRulesFacade;
import com.softwarearchetypes.accounting.postingrules.TargetAccounts;
import com.softwarearchetypes.common.Result;
import com.softwarearchetypes.common.events.InMemoryEventsPublisher;
import com.softwarearchetypes.quantity.money.Money;

import static com.softwarearchetypes.accounting.TransactionEntriesConstraint.BALANCING_CONSTRAINT;
import static com.softwarearchetypes.quantity.money.Money.pln;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

class LimitAuditScenarios {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-08T10:00:01Z");
    private static final Clock CLOCK = Clock.fixed(DECIDED_AT, UTC);
    private final AccountingFacade accounting = AccountingConfiguration.inMemory(CLOCK).facade();
    // Manual firing (L10): the detached publisher deliberately avoids automatic recursive rule execution.
    private final PostingRulesFacade rules = PostingRulesConfiguration.inMemory(accounting, new InMemoryEventsPublisher(), CLOCK).facade();
    private final Map<String, Map<String, AccountId>> customerAccounts = new HashMap<>();
    private final Map<String, PostingRuleId> decisionRules = new HashMap<>();
    private final AccountId funding = createAccount("BANK:FUNDING", "ASSET");
    private final AccountId grants = createAccount("BANK:LIMIT_GRANTS", "OFF_BALANCE");

    @BeforeEach
    void provisionCustomer() {
        provision("123", 500, 800);
    }

    @Test
    void attempted_daily_breach_is_recorded_without_moving_funds_or_using_allowances() {
        TransactionId request = request("123", 600, "auth-1");

        Result<String, Set<TransactionId>> result = process(request);

        assertThat(result.success()).isTrue();
        EntryView audit = decisionFor("123", request);
        assertThat(result.getSuccess()).containsExactly(audit.transactionId());
        assertThat(audit.amount()).isEqualTo(pln(600));
        assertThat(audit.metadata().metadata()).containsEntry("decision", "REJECTED")
                .containsEntry("violatedLimits", "DAILY")
                .containsEntry("dailyLimit", "500").containsEntry("dailyAvailable", "500")
                .containsEntry("dailyUsed", "0").containsEntry("monthlyLimit", "800")
                .containsEntry("dailyExcess", "100").containsEntry("monthlyExcess", "0");
        assertRejectedAudit(audit, request, "auth-1");
        assertBalances("123", 5000, 0, 500, 800);
        assertThat(pending("123")).isEmpty();
    }

    @Test
    void attempted_monthly_breach_identifies_the_monthly_dimension() {
        provision("789", 500, 200);
        TransactionId request = request("789", 300, "auth-monthly");

        assertThat(process(request).success()).isTrue();

        assertThat(decisionFor("789", request).metadata().metadata())
                .containsEntry("decision", "REJECTED").containsEntry("violatedLimits", "MONTHLY")
                .containsEntry("monthlyLimit", "200").containsEntry("monthlyExcess", "100");
        assertBalances("789", 5000, 0, 500, 200);
        assertThat(entries(account("123", "decisions"))).isEmpty();
    }

    @Test
    void one_audit_entry_can_explain_both_exceeded_limits() {
        TransactionId request = request("123", 900, "auth-both");

        assertThat(process(request).success()).isTrue();

        assertThat(decisionFor("123", request).metadata().metadata())
                .containsEntry("violatedLimits", "DAILY,MONTHLY")
                .containsEntry("dailyExcess", "400").containsEntry("monthlyExcess", "100");
        assertThat(entries(account("123", "decisions"))).hasSize(1);
        assertBalances("123", 5000, 0, 500, 800);
    }

    @Test
    void exact_limit_approval_records_the_decision_with_the_hold_and_allowance_usage() {
        TransactionId request = request("123", 500, "auth-exact");

        Result<String, Set<TransactionId>> result = process(request);

        assertThat(result.success()).isTrue();
        EntryView audit = decisionFor("123", request);
        assertThat(result.getSuccess()).containsExactly(audit.transactionId());
        assertThat(audit.metadata().metadata()).containsEntry("decision", "APPROVED")
                .containsEntry("violatedLimits", "").containsEntry("snapshotPhase", "BEFORE");
        assertThat(transactionEntries(audit.transactionId())).hasSize(7);
        assertThat(accounting.findTransactionBy(audit.transactionId()).orElseThrow().type())
                .isEqualTo(TransactionType.of("HOLD_AUTHORIZED"));
        assertBalances("123", 4500, 500, 0, 300);
        assertThat(transactionEntries(request)).hasSize(1);
    }

    @Test
    void rejection_retains_the_observed_usage_not_just_a_generic_failure_message() {
        assertThat(process(request("123", 400, "first")).success()).isTrue();
        TransactionId rejected = request("123", 200, "second");

        assertThat(process(rejected).success()).isTrue();

        assertThat(decisionFor("123", rejected).metadata().metadata())
                .containsEntry("dailyUsed", "400").containsEntry("monthlyUsed", "400")
                .containsEntry("dailyAvailable", "100").containsEntry("monthlyAvailable", "400")
                .containsEntry("requestedAmount", "200").containsEntry("reason", "Limit exceeded: DAILY");
        assertBalances("123", 4600, 400, 100, 400);
    }

    @Test
    void reprocessing_one_request_does_not_post_a_second_approval_or_audit() {
        TransactionId request = request("123", 100, "auth-retry");
        assertThat(process(request).success()).isTrue();
        List<AccountView> before = accounting.findAll();

        Result<String, Set<TransactionId>> retried = process(request);

        assertThat(retried.success()).isTrue();
        assertThat(retried.getSuccess()).isEmpty();
        assertThat(accounting.findAll()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void repeated_trigger_entries_in_one_batch_do_not_duplicate_the_decision() {
        TransactionId request = request("123", 600, "duplicate-trigger");
        EntryView trigger = transactionEntries(request).getFirst();

        Result<String, Set<TransactionId>> result = rules.executeRulesFor(List.of(trigger, trigger));

        assertThat(result.success()).isTrue();
        assertThat(result.getSuccess()).containsExactly(decisionFor("123", request).transactionId());
        assertThat(entries(account("123", "decisions"))).hasSize(1);
    }

    @Test
    void separate_requests_with_the_same_external_reference_keep_distinct_audit_identities() {
        TransactionId first = request("123", 600, "same-external-reference");
        TransactionId second = request("123", 600, "same-external-reference");

        assertThat(process(first).success()).isTrue();
        assertThat(process(second).success()).isTrue();

        assertThat(decisionFor("123", first).transactionId()).isNotEqualTo(decisionFor("123", second).transactionId());
        assertThat(entries(account("123", "decisions"))).hasSize(2);
    }

    @Test
    void an_actual_breach_is_audited_as_posted_not_misrepresented_as_a_rejected_attempt() {
        TransactionId override = override("123", 600);
        List<AccountView> before = financialAccounts("123");

        Result<String, Set<TransactionId>> result = process(override);

        assertThat(result.success()).isTrue();
        EntryView audit = decisionFor("123", override);
        assertThat(result.getSuccess()).containsExactly(audit.transactionId());
        assertThat(audit.metadata().metadata()).containsEntry("decision", "BREACHED")
                .containsEntry("snapshotPhase", "AFTER").containsEntry("violatedLimits", "DAILY")
                .containsEntry("dailyAvailable", "-100").containsEntry("dailyUsed", "600")
                .containsEntry("dailyExcess", "100");
        assertThat(accounting.findTransactionBy(audit.transactionId()).orElseThrow().type())
                .isEqualTo(TransactionType.of("LIMIT_BREACHED"));
        assertThat(before).allSatisfy(previous -> {
            AccountView current = accounting.findAccount(previous.id()).orElseThrow();
            assertThat(current.balance()).isEqualTo(previous.balance());
            assertThat(current.entries()).containsExactlyInAnyOrderElementsOf(previous.entries());
        });
        assertBalances("123", 4400, 600, -100, 200);
    }

    @Test
    void reprocessing_an_actual_breach_keeps_the_original_audit_record() {
        TransactionId override = override("123", 600);
        assertThat(process(override).success()).isTrue();
        EntryView original = decisionFor("123", override);

        Result<String, Set<TransactionId>> retried = process(override);

        assertThat(retried.success()).isTrue();
        assertThat(retried.getSuccess()).isEmpty();
        assertThat(entries(account("123", "decisions"))).containsExactly(original);
        assertBalances("123", 4400, 600, -100, 200);
    }

    @Test
    void an_override_within_limits_is_not_falsely_reported_as_a_breach() {
        TransactionId override = override("123", 100);

        Result<String, Set<TransactionId>> result = process(override);

        assertThat(result.success()).isTrue();
        assertThat(result.getSuccess()).isEmpty();
        assertThat(entries(account("123", "decisions"))).isEmpty();
    }

    @Test
    void eligibility_requires_account_direction_and_operation_on_the_same_entry() {
        Transaction decoy = accounting.transaction().occurredAt(REQUESTED_AT).appliesAt(REQUESTED_AT)
                .withTypeOf("DECOY").withMetadata("operation", "AUTHORIZE", "customerId", "123")
                .executing().debitFrom(account("123", "requests"), pln(10)).creditTo(grants, pln(10)).build();
        assertThat(accounting.execute(decoy).success()).isTrue();

        Result<String, Set<TransactionId>> result = process(decoy.id());

        assertThat(result.success()).isTrue();
        assertThat(result.getSuccess()).isEmpty();
        assertThat(entries(account("123", "decisions"))).isEmpty();
    }

    @Test
    void failed_posting_stops_the_batch_and_leaves_unresolved_requests_discoverable() {
        TransactionId first = request("123", 400, "first");
        TransactionId stale = request("123", 200, "stale");
        TransactionId later = request("123", 10, "later");
        List<EntryView> batch = new ArrayList<>(transactionEntries(first));
        batch.addAll(transactionEntries(stale));
        batch.addAll(transactionEntries(later));

        Result<String, Set<TransactionId>> result = rules.executeRulesFor(batch);

        assertThat(result.failure()).isTrue();
        assertThat(result.getFailure()).contains("negative balance");
        assertThat(entries(account("123", "decisions"))).hasSize(1);
        assertThat(pending("123")).containsExactlyInAnyOrder(stale, later);
        assertBalances("123", 4600, 400, 100, 400);
        assertThat(process(stale).success()).isTrue();
        assertThat(decisionFor("123", stale).metadata().metadata()).containsEntry("decision", "REJECTED");
        assertThat(process(later).success()).isTrue();
        assertThat(pending("123")).isEmpty();
    }

    @Test
    void missing_audit_account_reports_failure_and_preserves_the_request_for_retry() {
        assertThat(rules.deleteRule(decisionRules.get("123")).success()).isTrue();
        Map<String, AccountId> brokenTargets = new HashMap<>(customerAccounts.get("123"));
        brokenTargets.put("decisions", AccountId.generate());
        PostingRule broken = decisionRule("123", 500, 800, brokenTargets);
        assertThat(rules.saveRule(broken).success()).isTrue();
        TransactionId request = request("123", 600, "retry-after-repair");

        Result<String, Set<TransactionId>> failed = process(request);

        assertThat(failed.failure()).isTrue();
        assertThat(failed.getFailure()).contains("not found");
        assertThat(pending("123")).containsExactly(request);
        assertBalances("123", 5000, 0, 500, 800);
        assertThat(rules.deleteRule(broken.id()).success()).isTrue();
        assertThat(rules.saveRule(decisionRule("123", 500, 800, customerAccounts.get("123"))).success()).isTrue();
        assertThat(process(request).success()).isTrue();
        assertThat(decisionFor("123", request).metadata().metadata()).containsEntry("decision", "REJECTED");
    }

    @Test
    void invalid_audit_configuration_cannot_silently_approve_a_rejection() {
        assertThat(rules.deleteRule(decisionRules.get("123")).success()).isTrue();
        Map<String, AccountId> targets = new HashMap<>(customerAccounts.get("123"));
        targets.put("decisions", account("123", "wallet"));
        assertThat(rules.saveRule(decisionRule("123", 500, 800, targets)).success()).isTrue();
        TransactionId request = request("123", 600, "invalid-audit");

        Result<String, Set<TransactionId>> result = process(request);

        assertThat(result.failure()).isTrue();
        assertThat(result.getFailure()).contains("Entry balance");
        assertThat(pending("123")).containsExactly(request);
        assertBalances("123", 5000, 0, 500, 800);
    }

    @Test
    void a_request_without_a_decision_rule_remains_pending_not_rejected() {
        assertThat(rules.deleteRule(decisionRules.get("123")).success()).isTrue();
        TransactionId request = request("123", 600, "unconfigured");

        Result<String, Set<TransactionId>> result = process(request);

        assertThat(result.success()).isTrue();
        assertThat(result.getSuccess()).isEmpty();
        assertThat(pending("123")).containsExactly(request);
        assertBalances("123", 5000, 0, 500, 800);
    }

    @Test
    void audit_queries_select_rejections_without_counting_approved_amounts_or_other_customers() {
        provision("789", 100, 200);
        assertThat(process(request("123", 100, "approved")).success()).isTrue();
        assertThat(process(request("123", 600, "daily")).success()).isTrue();
        assertThat(process(request("123", 900, "both")).success()).isTrue();
        assertThat(process(request("789", 300, "other-customer")).success()).isTrue();
        AccountId projection = AccountId.generate();

        assertThat(accounting.createProjectingAccount(projection, AccountEntryFilter.filtering()
                .onAccountEquals(account("123", "decisions"))
                .havingMetadata("decision", "REJECTED"), "Rejected limit attempts").success()).isTrue();

        AccountView report = accounting.findAccount(projection).orElseThrow();
        assertThat(report.entries()).hasSize(2).allSatisfy(entry ->
                assertThat(entry.metadata().metadata()).containsEntry("customerId", "123"));
        assertThat(report.balance()).isEqualTo(pln(1500));
    }

    private void provision(String customer, int daily, int monthly) {
        String prefix = "CUSTOMER:" + customer + ":";
        Map<String, AccountId> accounts = Map.of(
                "requests", createAccount(prefix + "AUTHORIZATION_REQUESTS", "OFF_BALANCE"),
                "decisions", createAccount(prefix + "LIMIT_DECISIONS", "OFF_BALANCE"),
                "wallet", createAccount(prefix + "WALLET", "LIABILITY"),
                "held", createAccount(prefix + "HELD", "LIABILITY"),
                "dailyAvailable", createAccount(prefix + "DAILY:2026-10-08:AVAILABLE", "OFF_BALANCE"),
                "dailyUsed", createAccount(prefix + "DAILY:2026-10-08:USED", "OFF_BALANCE"),
                "monthlyAvailable", createAccount(prefix + "MONTHLY:2026-10:AVAILABLE", "OFF_BALANCE"),
                "monthlyUsed", createAccount(prefix + "MONTHLY:2026-10:USED", "OFF_BALANCE"));
        customerAccounts.put(customer, accounts);
        assertThat(accounting.transfer(funding, accounts.get("wallet"), pln(5000), REQUESTED_AT, REQUESTED_AT).success()).isTrue();
        assertThat(accounting.transfer(grants, accounts.get("dailyAvailable"), pln(daily), REQUESTED_AT, REQUESTED_AT).success()).isTrue();
        assertThat(accounting.transfer(grants, accounts.get("monthlyAvailable"), pln(monthly), REQUESTED_AT, REQUESTED_AT).success()).isTrue();
        PostingRule rule = decisionRule(customer, daily, monthly, accounts);
        assertThat(rules.saveRule(rule).success()).isTrue();
        decisionRules.put(customer, rule.id());
        PostingRule breachRule = PostingRuleBuilder.createRule("Audit actual breach: " + customer)
                .when(EligibilityCondition.custom(context -> context.triggeringEntries().stream()
                        .anyMatch(entry -> isOverride(entry, customer))))
                .transferTo(AccountFinder.fixed(accounts))
                .calculateUsing((targets, context) -> context.triggeringEntries().stream()
                        .filter(entry -> isOverride(entry, customer)).distinct()
                        .flatMap(entry -> decision(entry, targets, context, daily, monthly, true).stream()).toList())
                .build();
        assertThat(rules.saveRule(breachRule).success()).isTrue();
    }

    private PostingRule decisionRule(String customer, int daily, int monthly, Map<String, AccountId> accounts) {
        // Posting Rule (L10): compose eligibility, account selection and calculation without a Limit entity.
        return PostingRuleBuilder.createRule("Authorization decision: " + customer)
                .when(EligibilityCondition.custom(context -> context.triggeringEntries().stream()
                        .anyMatch(entry -> isRequest(entry, customer))))
                .transferTo(AccountFinder.fixed(Map.copyOf(accounts)))
                .calculateUsing((targets, context) -> context.triggeringEntries().stream()
                        .filter(entry -> isRequest(entry, customer)).distinct()
                        .flatMap(entry -> decision(entry, targets, context, daily, monthly, false).stream()).toList())
                .build();
    }

    private List<Transaction> decision(EntryView source, TargetAccounts targets, PostingContext context,
                                       int daily, int monthly, boolean actualBreach) {
        String sourceId = source.transactionId().value().toString();
        AccountView audit = targets.getRequired("decisions");
        // Ledger-based deduplication: a recorded decision, not an in-memory flag, resolves the request.
        if (audit.entries().stream().anyMatch(entry -> sourceId.equals(entry.metadata().metadata().get("sourceTransactionId")))) {
            return List.of();
        }
        Money amount = source.amount().abs();
        Money dailyAvailable = targets.getRequired("dailyAvailable").balance();
        Money monthlyAvailable = targets.getRequired("monthlyAvailable").balance();
        Money dailyExcess = Money.max(pln(0), actualBreach ? dailyAvailable.negate() : amount.subtract(dailyAvailable));
        Money monthlyExcess = Money.max(pln(0), actualBreach ? monthlyAvailable.negate() : amount.subtract(monthlyAvailable));
        List<String> violations = new ArrayList<>();
        if (!dailyExcess.isZero()) violations.add("DAILY");
        if (!monthlyExcess.isZero()) violations.add("MONTHLY");
        if (actualBreach && violations.isEmpty()) return List.of();
        String outcome = actualBreach ? "BREACHED" : violations.isEmpty() ? "APPROVED" : "REJECTED";
        Map<String, String> metadata = new HashMap<>(source.metadata().metadata());
        metadata.put("sourceTransactionId", sourceId);
        metadata.put("decision", outcome);
        metadata.put("snapshotPhase", actualBreach ? "AFTER" : "BEFORE");
        metadata.put("requestedAmount", amount.value().toPlainString());
        metadata.put("currency", amount.currency());
        metadata.put("dailyLimit", Integer.toString(daily));
        metadata.put("monthlyLimit", Integer.toString(monthly));
        metadata.put("dailyAvailable", dailyAvailable.value().toPlainString());
        metadata.put("monthlyAvailable", monthlyAvailable.value().toPlainString());
        metadata.put("dailyUsed", targets.getRequired("dailyUsed").balance().value().toPlainString());
        metadata.put("monthlyUsed", targets.getRequired("monthlyUsed").balance().value().toPlainString());
        metadata.put("dailyExcess", dailyExcess.value().toPlainString());
        metadata.put("monthlyExcess", monthlyExcess.value().toPlainString());
        metadata.put("dailyPeriod", "2026-10-08");
        metadata.put("monthlyPeriod", "2026-10");
        metadata.put("violatedLimits", String.join(",", violations));
        metadata.put("reason", violations.isEmpty() ? "Within configured limits" : "Limit exceeded: " + String.join(",", violations));
        String type = switch (outcome) {
            case "APPROVED" -> "HOLD_AUTHORIZED";
            case "REJECTED" -> "LIMIT_ATTEMPT_REJECTED";
            default -> "LIMIT_BREACHED";
        };
        var builder = context.accountingFacade().transaction().occurredAt(context.executionTime()).appliesAt(context.executionTime())
                .withTypeOf(type).withMetadata(MetaData.of(metadata))
                .withTransactionEntriesConstraint(outcome.equals("APPROVED")
                        ? BALANCING_CONSTRAINT.and(new NonNegativeBalanceConstraint(Set.of(
                                targets.getRequired("wallet").id(), targets.getRequired("dailyAvailable").id(), targets.getRequired("monthlyAvailable").id())))
                        : BALANCING_CONSTRAINT)
                .executing();
        if (outcome.equals("APPROVED")) {
            builder.debitFrom(targets.getRequired("wallet").id(), amount).creditTo(targets.getRequired("held").id(), amount)
                    .debitFrom(targets.getRequired("dailyAvailable").id(), amount).creditTo(targets.getRequired("dailyUsed").id(), amount)
                    .debitFrom(targets.getRequired("monthlyAvailable").id(), amount).creditTo(targets.getRequired("monthlyUsed").id(), amount);
        }
        // Memo Account (L08): an audit amount is information, not spendable money or additional limit usage.
        return List.of(builder.creditTo(audit.id(), amount).build());
    }

    private boolean isRequest(EntryView entry, String customer) {
        return entry.accountId().equals(account(customer, "requests")) && entry.type() == EntryView.EntryType.CREDIT
                && "AUTHORIZE".equals(entry.metadata().metadata().get("operation"));
    }

    private boolean isOverride(EntryView entry, String customer) {
        return entry.accountId().equals(account(customer, "dailyAvailable")) && entry.type() == EntryView.EntryType.DEBIT
                && "OVERRIDE".equals(entry.metadata().metadata().get("operation"));
    }

    private TransactionId request(String customer, int amount, String authorizationId) {
        // Persist intent first: an interrupted decision remains visible as a request with no outcome entry.
        Transaction request = accounting.transaction().occurredAt(REQUESTED_AT).appliesAt(REQUESTED_AT)
                .withTypeOf("AUTHORIZATION_REQUESTED")
                .withMetadata("customerId", customer, "authorizationId", authorizationId, "operation", "AUTHORIZE", "reason", "Card authorization")
                .executing().creditTo(account(customer, "requests"), pln(amount)).build();
        assertThat(accounting.execute(request).success()).isTrue();
        return request.id();
    }

    private TransactionId override(String customer, int amount) {
        Transaction override = accounting.transaction().occurredAt(REQUESTED_AT).appliesAt(REQUESTED_AT)
                .withTypeOf("LIMIT_OVERRIDE").withMetadata("customerId", customer, "authorizationId", "import-1", "operation", "OVERRIDE")
                .executing().debitFrom(account(customer, "wallet"), pln(amount)).creditTo(account(customer, "held"), pln(amount))
                .debitFrom(account(customer, "dailyAvailable"), pln(amount)).creditTo(account(customer, "dailyUsed"), pln(amount))
                .debitFrom(account(customer, "monthlyAvailable"), pln(amount)).creditTo(account(customer, "monthlyUsed"), pln(amount)).build();
        assertThat(accounting.execute(override).success()).isTrue();
        return override.id();
    }

    private Result<String, Set<TransactionId>> process(TransactionId source) {
        return rules.executeRulesFor(transactionEntries(source));
    }

    private List<TransactionId> pending(String customer) {
        Set<String> resolved = entries(account(customer, "decisions")).stream()
                .map(entry -> entry.metadata().metadata().get("sourceTransactionId"))
                .collect(java.util.stream.Collectors.toSet());
        return entries(account(customer, "requests")).stream().filter(entry -> isRequest(entry, customer))
                .map(EntryView::transactionId).filter(id -> !resolved.contains(id.value().toString())).toList();
    }

    private EntryView decisionFor(String customer, TransactionId source) {
        return entries(account(customer, "decisions")).stream()
                .filter(entry -> source.value().toString().equals(entry.metadata().metadata().get("sourceTransactionId")))
                .findFirst().orElseThrow();
    }

    private void assertRejectedAudit(EntryView audit, TransactionId source, String authorization) {
        assertThat(audit.metadata().metadata()).containsEntry("customerId", "123")
                .containsEntry("authorizationId", authorization).containsEntry("sourceTransactionId", source.value().toString())
                .containsEntry("dailyPeriod", "2026-10-08").containsEntry("monthlyPeriod", "2026-10")
                .containsEntry("currency", "PLN");
        assertThat(audit.occurredAt()).isEqualTo(DECIDED_AT);
        assertThat(audit.appliesAt()).isEqualTo(DECIDED_AT);
        assertThat(accounting.findTransactionBy(audit.transactionId()).orElseThrow().type())
                .isEqualTo(TransactionType.of("LIMIT_ATTEMPT_REJECTED"));
        assertThat(transactionEntries(audit.transactionId())).containsExactly(audit);
        assertThat(transactionEntries(source)).singleElement().satisfies(entry ->
                assertThat(entry.occurredAt()).isEqualTo(REQUESTED_AT));
    }

    private void assertBalances(String customer, int wallet, int held, int daily, int monthly) {
        assertThat(accounting.balance(account(customer, "wallet"))).hasValue(pln(wallet));
        assertThat(accounting.balance(account(customer, "held"))).hasValue(pln(held));
        assertThat(accounting.balance(account(customer, "dailyAvailable"))).hasValue(pln(daily));
        assertThat(accounting.balance(account(customer, "monthlyAvailable"))).hasValue(pln(monthly));
    }

    private List<AccountView> financialAccounts(String customer) {
        return List.of("wallet", "held", "dailyAvailable", "dailyUsed", "monthlyAvailable", "monthlyUsed").stream()
                .map(role -> accounting.findAccount(account(customer, role)).orElseThrow()).toList();
    }

    private List<EntryView> transactionEntries(TransactionId id) {
        return accounting.findTransactionBy(id).orElseThrow().entries().stream()
                .flatMap(accountEntries -> accountEntries.entries().stream()).toList();
    }

    private List<EntryView> entries(AccountId id) {
        return accounting.findAccount(id).orElseThrow().entries();
    }

    private AccountId account(String customer, String role) {
        return customerAccounts.get(customer).get(role);
    }

    private AccountId createAccount(String name, String type) {
        var result = accounting.createAccount(CreateAccount.generate(name, type));
        assertThat(result.success()).isTrue();
        return result.getSuccess();
    }
}
