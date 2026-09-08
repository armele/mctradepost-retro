package com.deathfrog.mctradepost.api.event;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * Fired on the NeoForge event bus after a colony completes a dawn incentive payroll with at least one cycle payment.
 * The event is posted on the server thread after withdrawals and skill adjustments have been applied.
 */
public final class CitizenIncentivesPaidEvent extends Event
{
    private final IColony colony;
    private final List<Payment> payments;
    private final long totalAmount;

    /**
     * Creates an immutable account of one completed colony payroll.
     * @param colony colony that funded the incentives
     * @param payments successful citizen payments in payroll priority order
     */
    @SuppressWarnings("null")
    public CitizenIncentivesPaidEvent(final IColony colony, final List<Payment> payments)
    {
        this.colony = colony;
        this.payments = List.copyOf(payments);
        this.totalAmount = payments.stream().mapToLong(Payment::getAmount).sum();
    }

    /** @return colony that funded the incentives */
    public IColony getColony()
    {
        return colony;
    }

    /** @return immutable successful payments in payroll priority order */
    public List<Payment> getPayments()
    {
        return payments;
    }

    /** @return total economic units withdrawn by this payroll */
    public long getTotalAmount()
    {
        return totalAmount;
    }

    /** One citizen's successful, all-or-nothing incentive payment. */
    public static final class Payment
    {
        private final ICitizenData citizen;
        private final long amount;

        /**
         * Creates a successful citizen payment entry.
         * @param citizen citizen whose incentive package was paid
         * @param amount economic units withdrawn for the citizen's pay cycle
         */
        public Payment(final ICitizenData citizen, final long amount)
        {
            this.citizen = citizen;
            this.amount = amount;
        }

        /** @return citizen whose incentive package was paid */
        public ICitizenData getCitizen()
        {
            return citizen;
        }

        /** @return economic units withdrawn for the citizen's pay cycle */
        public long getAmount()
        {
            return amount;
        }
    }
}
