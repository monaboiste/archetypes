package com.softwarearchetypes.rules.core;

import com.softwarearchetypes.rules.core.selection.CandidateRule;
import com.softwarearchetypes.rules.core.selection.RuleSelector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The point of L03: this is a rule core, not a discount core. The same building blocks drive a
// completely different domain - credit limits - with no OfferItem and no Money anywhere in sight.
// The only domain-aware piece is the ChangeApplicator below.
public class RuleCoreReusabilityTest {

    record Application(String segment, int limit) {
    }

    record Applicant(String segment, int yearsOfHistory) {
    }

    private static final ChangeApplicator<Application, Integer> LIMIT = new ChangeApplicator<>() {
        @Override
        public Integer currentValue(Application application) {
            return application.limit();
        }

        @Override
        public Application applyChange(Application application, Integer newLimit, String description) {
            return new Application(application.segment(), newLimit);
        }
    };

    @Test
    public void chainAppliesModifiersInOrder() {
        ChainModifier<Application> chain = new ChainModifier<Application>()
                .add(raiseLimitBy(500, 10_000))
                .add(raiseLimitBy(200, 10_000));

        assertEquals(1700, chain.modify(new Application("retail", 1000)).limit());
    }

    @Test
    public void guardianRejectsAForbiddenResult() {
        Modifier<Application> tooGenerous = raiseLimitBy(5000, 3000);

        assertEquals(1000, tooGenerous.modify(new Application("retail", 1000)).limit());
    }

    @Test
    public void selectionSeesTheSelectionContextOnly() {
        List<CandidateRule<Applicant, Application>> rules = List.of(
                new CandidateRule<>(raiseLimitBy(500, 10_000), applicant -> applicant.yearsOfHistory() >= 3),
                new CandidateRule<>(raiseLimitBy(9000, 10_000), applicant -> applicant.segment().equals("vip")));

        Modifier<Application> forLoyalRetail = RuleSelector.select(new Applicant("retail", 5), rules);

        assertEquals(1500, forLoyalRetail.modify(new Application("retail", 1000)).limit());
    }

    @Test
    public void identityModifierChangesNothing() {
        Application application = new Application("retail", 1000);

        assertEquals(application, new IdentityModifier<Application>().modify(application));
    }

    private static ConfigurableModifier<Application, Integer> raiseLimitBy(int amount, int maxLimit) {
        return new ConfigurableModifier<>(
                "raise by " + amount,
                application -> application.limit() > 0,          // predicate
                application -> application.limit() + amount,     // applier
                application -> application.limit() <= maxLimit,  // guardian
                LIMIT);
    }
}
