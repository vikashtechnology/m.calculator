package com.advanceai.calculator.engine

import java.math.BigInteger
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The "everyday" calculators: money, tax, health, percentages and a few extra
 * algebra helpers. Everything here is pure Kotlin so it can be unit tested.
 */
object Everyday {

    // ---- GST ---------------------------------------------------------------

    data class GstResult(
        val amount: Double,
        val ratePercent: Double,
        val inclusive: Boolean,
        val base: Double,
        val gst: Double,
        val total: Double,
        val cgst: Double,
        val sgst: Double
    )

    /**
     * GST split into CGST and SGST (each half the rate), which is how intra-state
     * invoices are written. [inclusive] treats the input as the final price.
     */
    fun gst(amount: Double, ratePercent: Double, inclusive: Boolean): GstResult {
        require(amount >= 0) { "Enter an amount of zero or more" }
        require(ratePercent >= 0) { "The GST rate cannot be negative" }
        val base: Double
        val tax: Double
        if (inclusive) {
            base = amount / (1 + ratePercent / 100.0)
            tax = amount - base
        } else {
            base = amount
            tax = amount * ratePercent / 100.0
        }
        return GstResult(
            amount = amount,
            ratePercent = ratePercent,
            inclusive = inclusive,
            base = base,
            gst = tax,
            total = base + tax,
            cgst = tax / 2,
            sgst = tax / 2
        )
    }

    // ---- Income tax (India) -------------------------------------------------

    enum class TaxRegime(val label: String) {
        NEW("New regime"),
        OLD("Old regime")
    }

    data class Slab(val upTo: Double, val ratePercent: Double)

    /** Slab boundaries and rates are data so a new budget only needs new numbers. */
    val newRegimeSlabs: List<Slab> = listOf(
        Slab(400_000.0, 0.0),
        Slab(800_000.0, 5.0),
        Slab(1_200_000.0, 10.0),
        Slab(1_600_000.0, 15.0),
        Slab(2_000_000.0, 20.0),
        Slab(2_400_000.0, 25.0),
        Slab(Double.MAX_VALUE, 30.0)
    )

    val oldRegimeSlabs: List<Slab> = listOf(
        Slab(250_000.0, 0.0),
        Slab(500_000.0, 5.0),
        Slab(1_000_000.0, 20.0),
        Slab(Double.MAX_VALUE, 30.0)
    )

    data class SlabHit(val from: Double, val to: Double, val ratePercent: Double, val tax: Double)

    data class TaxResult(
        val regime: TaxRegime,
        val grossIncome: Double,
        val standardDeduction: Double,
        val otherDeductions: Double,
        val taxableIncome: Double,
        val slabTax: Double,
        val rebate: Double,
        val marginalRelief: Double,
        val cess: Double,
        val totalTax: Double,
        val netIncome: Double,
        val effectiveRatePercent: Double,
        val marginalRatePercent: Double,
        val breakdown: List<SlabHit>
    )

    /**
     * Income tax for an individual resident, financial year 2025-26.
     *
     * New regime: nil up to 4 lakh, then 5/10/15/20/25/30% slabs, section 87A
     * rebate up to 12 lakh taxable, marginal relief just above it, 4% cess.
     * Old regime: 2.5/5/20/30% slabs, rebate up to 5 lakh, 4% cess.
     */
    fun incomeTax(
        grossAnnual: Double,
        regime: TaxRegime = TaxRegime.NEW,
        salaried: Boolean = true,
        otherDeductions: Double = 0.0
    ): TaxResult {
        require(grossAnnual >= 0) { "Enter an income of zero or more" }
        val standardDeduction = if (!salaried) 0.0 else if (regime == TaxRegime.NEW) 75_000.0 else 50_000.0
        val deductions = if (regime == TaxRegime.NEW) standardDeduction else standardDeduction + otherDeductions
        val taxable = (grossAnnual - deductions).coerceAtLeast(0.0)

        val slabs = if (regime == TaxRegime.NEW) newRegimeSlabs else oldRegimeSlabs
        val hits = ArrayList<SlabHit>()
        var remaining = taxable
        var previous = 0.0
        var slabTax = 0.0
        var marginalRate = 0.0
        for (slab in slabs) {
            if (remaining <= 0) break
            val width = (slab.upTo - previous).coerceAtLeast(0.0)
            val inSlab = minOf(remaining, width)
            if (inSlab > 0) {
                val tax = inSlab * slab.ratePercent / 100.0
                slabTax += tax
                hits.add(SlabHit(previous, previous + inSlab, slab.ratePercent, tax))
                marginalRate = slab.ratePercent
            }
            remaining -= inSlab
            previous = slab.upTo
        }

        // Section 87A rebate.
        val rebateLimit = if (regime == TaxRegime.NEW) 1_200_000.0 else 500_000.0
        val rebateCap = if (regime == TaxRegime.NEW) 60_000.0 else 12_500.0
        val rebate = if (taxable <= rebateLimit) minOf(slabTax, rebateCap) else 0.0

        // Marginal relief: just above the rebate limit the tax cannot exceed the
        // extra income above the limit (new regime only).
        var relief = 0.0
        if (regime == TaxRegime.NEW && taxable > rebateLimit) {
            val afterRebate = slabTax - rebate
            val excess = taxable - rebateLimit
            if (afterRebate > excess) relief = afterRebate - excess
        }

        val taxAfterRelief = (slabTax - rebate - relief).coerceAtLeast(0.0)
        val cess = taxAfterRelief * 0.04
        val total = taxAfterRelief + cess
        val effective = if (grossAnnual > 0) total / grossAnnual * 100.0 else 0.0

        return TaxResult(
            regime = regime,
            grossIncome = grossAnnual,
            standardDeduction = standardDeduction,
            otherDeductions = otherDeductions,
            taxableIncome = taxable,
            slabTax = slabTax,
            rebate = rebate,
            marginalRelief = relief,
            cess = cess,
            totalTax = total,
            netIncome = grossAnnual - total,
            effectiveRatePercent = effective,
            marginalRatePercent = marginalRate,
            breakdown = hits
        )
    }

    // ---- EMI ----------------------------------------------------------------

    data class EmiResult(
        val principal: Double,
        val annualRatePercent: Double,
        val months: Int,
        val emi: Double,
        val totalPayment: Double,
        val totalInterest: Double,
        val interestPercent: Double
    )

    /** Standard reducing-balance EMI. A zero rate simply spreads the principal. */
    fun emi(principal: Double, annualRatePercent: Double, months: Int): EmiResult {
        require(principal > 0) { "Enter a loan amount greater than zero" }
        require(months > 0) { "Enter a tenure of at least one month" }
        require(annualRatePercent >= 0) { "The interest rate cannot be negative" }

        val monthlyRate = annualRatePercent / 12.0 / 100.0
        val emi = if (monthlyRate == 0.0) {
            principal / months
        } else {
            val factor = Math.pow(1 + monthlyRate, months.toDouble())
            principal * monthlyRate * factor / (factor - 1)
        }
        val total = emi * months
        return EmiResult(
            principal = principal,
            annualRatePercent = annualRatePercent,
            months = months,
            emi = emi,
            totalPayment = total,
            totalInterest = total - principal,
            interestPercent = (total - principal) / principal * 100.0
        )
    }

    /** Month-by-month schedule, limited so a huge tenure cannot freeze the UI. */
    data class AmortRow(
        val month: Int,
        val interest: Double,
        val principalPaid: Double,
        val balance: Double
    )

    fun amortisation(result: EmiResult, limit: Int = 12): List<AmortRow> {
        val monthlyRate = result.annualRatePercent / 12.0 / 100.0
        var balance = result.principal
        val rows = ArrayList<AmortRow>()
        for (month in 1..minOf(result.months, limit.coerceAtLeast(1))) {
            val interest = balance * monthlyRate
            val principalPaid = (result.emi - interest).coerceAtMost(balance)
            balance = (balance - principalPaid).coerceAtLeast(0.0)
            rows.add(AmortRow(month, interest, principalPaid, balance))
        }
        return rows
    }

    // ---- BMI ----------------------------------------------------------------

    data class BmiResult(
        val bmi: Double,
        val category: String,
        val note: String,
        val healthyMinKg: Double,
        val healthyMaxKg: Double
    )

    /** WHO categories. Height in centimetres, weight in kilograms. */
    fun bmi(weightKg: Double, heightCm: Double): BmiResult {
        require(weightKg > 0) { "Enter a weight greater than zero" }
        require(heightCm > 0) { "Enter a height greater than zero" }
        val meters = heightCm / 100.0
        val value = weightKg / (meters * meters)
        val (category, note) = when {
            value < 16.0 -> "Severe thinness" to "Please speak to a doctor."
            value < 18.5 -> "Underweight" to "Below the healthy range."
            value < 25.0 -> "Normal weight" to "Within the healthy range."
            value < 30.0 -> "Overweight" to "Above the healthy range."
            value < 35.0 -> "Obese (class I)" to "Please speak to a doctor."
            value < 40.0 -> "Obese (class II)" to "Please speak to a doctor."
            else -> "Obese (class III)" to "Please speak to a doctor."
        }
        return BmiResult(
            bmi = value,
            category = category,
            note = note,
            healthyMinKg = 18.5 * meters * meters,
            healthyMaxKg = 24.9 * meters * meters
        )
    }

    // ---- Interest -----------------------------------------------------------

    data class InterestResult(
        val principal: Double,
        val annualRatePercent: Double,
        val years: Double,
        val compoundsPerYear: Int,
        val simpleInterest: Double,
        val simpleTotal: Double,
        val compoundInterest: Double,
        val compoundTotal: Double
    )

    fun interest(
        principal: Double,
        annualRatePercent: Double,
        years: Double,
        compoundsPerYear: Int
    ): InterestResult {
        require(principal > 0) { "Enter an amount greater than zero" }
        require(years > 0) { "Enter a duration greater than zero" }
        require(annualRatePercent >= 0) { "The rate cannot be negative" }
        require(compoundsPerYear > 0) { "Compounding must happen at least once a year" }

        val simple = principal * annualRatePercent * years / 100.0
        val n = compoundsPerYear.toDouble()
        val compound = principal * (Math.pow(1 + annualRatePercent / (100.0 * n), n * years) - 1)
        return InterestResult(
            principal = principal,
            annualRatePercent = annualRatePercent,
            years = years,
            compoundsPerYear = compoundsPerYear,
            simpleInterest = simple,
            simpleTotal = principal + simple,
            compoundInterest = compound,
            compoundTotal = principal + compound
        )
    }

    // ---- Percentages, discounts, margin -------------------------------------

    data class PercentResult(
        val percentOf: Double,      // x% of y
        val whatPercent: Double,    // x is what % of y
        val changePercent: Double,  // from x to y
        val increase: Double,       // y increased by x%
        val decrease: Double        // y decreased by x%
    )

    fun percent(x: Double, y: Double): PercentResult {
        require(y != 0.0 || (x == 0.0)) { "The second value cannot be zero" }
        return PercentResult(
            percentOf = x / 100.0 * y,
            whatPercent = if (y == 0.0) Double.NaN else x / y * 100.0,
            changePercent = if (x == 0.0) Double.NaN else (y - x) / x * 100.0,
            increase = y * (1 + x / 100.0),
            decrease = y * (1 - x / 100.0)
        )
    }

    data class DiscountResult(
        val listPrice: Double,
        val discountPercent: Double,
        val extraPercent: Double,
        val discountAmount: Double,
        val extraAmount: Double,
        val totalDiscount: Double,
        val payable: Double,
        val effectiveDiscountPercent: Double
    )

    /** Two successive discounts, applied one after the other as shops do. */
    fun discount(listPrice: Double, discountPercent: Double, extraPercent: Double = 0.0): DiscountResult {
        require(listPrice >= 0) { "Enter a price of zero or more" }
        val first = listPrice * discountPercent / 100.0
        val afterFirst = listPrice - first
        val second = afterFirst * extraPercent / 100.0
        val payable = afterFirst - second
        val total = first + second
        return DiscountResult(
            listPrice = listPrice,
            discountPercent = discountPercent,
            extraPercent = extraPercent,
            discountAmount = first,
            extraAmount = second,
            totalDiscount = total,
            payable = payable,
            effectiveDiscountPercent = if (listPrice > 0) total / listPrice * 100.0 else 0.0
        )
    }

    data class MarginResult(
        val cost: Double,
        val sellingPrice: Double,
        val profit: Double,
        val profitPercent: Double,  // profit on cost
        val marginPercent: Double,  // profit on selling price
        val markup: Double
    )

    fun margin(cost: Double, sellingPrice: Double): MarginResult {
        require(cost > 0) { "Enter a cost greater than zero" }
        val profit = sellingPrice - cost
        return MarginResult(
            cost = cost,
            sellingPrice = sellingPrice,
            profit = profit,
            profitPercent = profit / cost * 100.0,
            marginPercent = if (sellingPrice != 0.0) profit / sellingPrice * 100.0 else Double.NaN,
            markup = sellingPrice / cost
        )
    }

    // ---- Algebra helpers ----------------------------------------------------

    data class QuadraticResult(
        val a: Double,
        val b: Double,
        val c: Double,
        val discriminant: Double,
        val root1: Num,
        val root2: Num,
        val realRoots: Boolean,
        val vertexX: Double,
        val vertexY: Double,
        val sumOfRoots: Double,
        val productOfRoots: Double
    )

    fun quadratic(a: Double, b: Double, c: Double): QuadraticResult {
        require(a != 0.0) { "A quadratic needs a non-zero coefficient for x²" }
        val d = b * b - 4 * a * c
        val vertexX = -b / (2 * a)
        val vertexY = a * vertexX * vertexX + b * vertexX + c
        val (r1, r2, real) = when {
            d > 0 -> Triple(
                Num((-b + Math.sqrt(d)) / (2 * a)),
                Num((-b - Math.sqrt(d)) / (2 * a)),
                true
            )
            d == 0.0 -> Triple(Num(-b / (2 * a)), Num(-b / (2 * a)), true)
            else -> {
                val re = -b / (2 * a)
                val im = Math.sqrt(-d) / (2 * a)
                Triple(Num(re, im), Num(re, -im), false)
            }
        }
        return QuadraticResult(
            a = a, b = b, c = c,
            discriminant = d,
            root1 = r1,
            root2 = r2,
            realRoots = real,
            vertexX = vertexX,
            vertexY = vertexY,
            sumOfRoots = -b / a,
            productOfRoots = c / a
        )
    }

    /** Prime check that never loops past the square root. */
    fun isPrime(n: Long): Boolean {
        if (n < 2) return false
        if (n < 4) return true
        if (n % 2 == 0L) return false
        var i = 3L
        while (i * i <= n) {
            if (n % i == 0L) return false
            i += 2
        }
        return true
    }

    /** Trial division up to the square root, which is plenty for hand typed numbers. */
    fun primeFactors(n: Long): List<Long> {
        require(n > 1) { "Enter a whole number greater than 1" }
        val factors = ArrayList<Long>()
        var value = n
        var divisor = 2L
        while (divisor * divisor <= value) {
            while (value % divisor == 0L) {
                factors.add(divisor)
                value /= divisor
            }
            divisor++
        }
        if (value > 1) factors.add(value)
        return factors
    }

    fun factorString(factors: List<Long>): String {
        if (factors.isEmpty()) return "—"
        val groups = factors.groupingBy { it }.eachCount().toList().sortedBy { it.first }
        return groups.joinToString(" × ") { (prime, count) ->
            if (count == 1) "$prime" else "$prime^$count"
        }
    }

    /** Converts between bases 2-36, using BigInteger so long values stay exact. */
    fun baseConvert(value: String, fromBase: Int, toBase: Int): String {
        require(fromBase in 2..36) { "The 'from' base must be between 2 and 36" }
        require(toBase in 2..36) { "The 'to' base must be between 2 and 36" }
        val cleaned = value.trim().replace(" ", "").uppercase().removePrefix("0X").removePrefix("0B").removePrefix("0O")
        require(cleaned.isNotEmpty()) { "Enter a value to convert" }
        val parsed = try {
            BigInteger(cleaned, fromBase)
        } catch (e: NumberFormatException) {
            throw MathError("'$value' is not a valid base $fromBase number")
        }
        return parsed.toString(toBase).uppercase()
    }

    fun groupDigits(value: String, groupSize: Int): String {
        if (groupSize <= 0) return value
        val negative = value.startsWith("-")
        val digits = if (negative) value.drop(1) else value
        val grouped = digits.reversed().chunked(groupSize).joinToString(" ").reversed()
        return if (negative) "-$grouped" else grouped
    }

    // ---- Age ---------------------------------------------------------------

    data class AgeResult(
        val years: Int,
        val months: Int,
        val days: Int,
        val totalDays: Long,
        val totalWeeks: Long,
        val totalMonths: Long,
        val nextBirthdayInDays: Long
    )

    fun age(dateOfBirth: LocalDate, today: LocalDate): AgeResult {
        require(!dateOfBirth.isAfter(today)) { "The date of birth is in the future" }
        var years = ChronoUnit.YEARS.between(dateOfBirth, today).toInt()
        var anchor = dateOfBirth.plusYears(years.toLong())
        var months = ChronoUnit.MONTHS.between(anchor, today).toInt()
        anchor = anchor.plusMonths(months.toLong())
        val days = ChronoUnit.DAYS.between(anchor, today).toInt()

        var nextBirthday = dateOfBirth.plusYears(years.toLong() + 1)
        if (nextBirthday.isBefore(today)) nextBirthday = nextBirthday.plusYears(1)

        return AgeResult(
            years = years,
            months = months,
            days = days,
            totalDays = ChronoUnit.DAYS.between(dateOfBirth, today),
            totalWeeks = ChronoUnit.WEEKS.between(dateOfBirth, today),
            totalMonths = ChronoUnit.MONTHS.between(dateOfBirth, today),
            nextBirthdayInDays = ChronoUnit.DAYS.between(today, nextBirthday)
        )
    }

    /**
     * The distance between two dates, counted the way a calendar does: whole
     * years, then months, then the days left over, plus the plain totals.
     */
    data class DateSpan(
        val years: Int,
        val months: Int,
        val days: Int,
        val totalDays: Long,
        val totalWeeks: Long,
        val totalMonths: Long,
        val forward: Boolean
    )

    fun daysBetween(from: LocalDate, to: LocalDate): DateSpan {
        val forward = !to.isBefore(from)
        val start = if (forward) from else to
        val end = if (forward) to else from

        val years = ChronoUnit.YEARS.between(start, end).toInt()
        var anchor = start.plusYears(years.toLong())
        val months = ChronoUnit.MONTHS.between(anchor, end).toInt()
        anchor = anchor.plusMonths(months.toLong())
        val days = ChronoUnit.DAYS.between(anchor, end).toInt()

        return DateSpan(
            years = years,
            months = months,
            days = days,
            totalDays = ChronoUnit.DAYS.between(start, end),
            totalWeeks = ChronoUnit.WEEKS.between(start, end),
            totalMonths = ChronoUnit.MONTHS.between(start, end),
            forward = forward
        )
    }

    /** Moves a date by whole days, weeks, months or years. */
    data class DateShift(
        val date: LocalDate,
        val dayOfWeek: String,
        val daysInMonth: Int
    )

    fun shiftDate(
        from: LocalDate,
        days: Long = 0,
        weeks: Long = 0,
        months: Long = 0,
        years: Long = 0
    ): DateShift {
        val moved = from
            .plusDays(days + weeks * 7)
            .plusMonths(months)
            .plusYears(years)
        return DateShift(
            date = moved,
            dayOfWeek = moved.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            daysInMonth = moved.lengthOfMonth()
        )
    }

    /** Parses dd/mm/yyyy, dd-mm-yyyy or yyyy-mm-dd. */
    fun parseDate(text: String): LocalDate {
        val parts = text.trim().split(Regex("[/\\-.]")).filter { it.isNotBlank() }
        if (parts.size != 3) throw MathError("Use a date like 15/08/1998")
        val numbers = parts.map { it.toIntOrNull() ?: throw MathError("'$it' is not a number") }
        return when {
            parts[0].length == 4 -> LocalDate.of(numbers[0], numbers[1], numbers[2])
            else -> LocalDate.of(numbers[2], numbers[1], numbers[0])
        }
    }
}
