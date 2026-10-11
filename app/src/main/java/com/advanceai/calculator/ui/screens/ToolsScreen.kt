package com.advanceai.calculator.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.advanceai.calculator.ui.theme.appChipColors
import com.advanceai.calculator.engine.Everyday
import com.advanceai.calculator.engine.Formatter
import com.advanceai.calculator.engine.MathError
import java.time.LocalDate

/** The everyday calculators reachable from the drop-down menu. */
enum class EverydayTool(val label: String, val title: String) {
    GST("GST", "GST calculator"),
    TAX("Tax", "Income tax (India, FY 2025-26)"),
    EMI("EMI", "Loan EMI calculator"),
    BMI("BMI", "Body mass index"),
    PERCENT("Percent", "Percentage and discount"),
    INTEREST("Interest", "Simple and compound interest"),
    PROFIT("Profit", "Profit, loss and margin"),
    ALGEBRA("Algebra", "Quadratic, primes and factors"),
    BASES("Bases", "Number base converter"),
    AGE("Age", "Age calculator")
}

@Composable
fun ToolsScreen(
    tool: EverydayTool,
    onSelectTool: (EverydayTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EverydayTool.entries.forEach { entry ->
                FilterChip(
                    colors = appChipColors(),
                    selected = entry == tool,
                    onClick = { onSelectTool(entry) },
                    label = { Text(entry.label) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleLarge
            )
            when (tool) {
                EverydayTool.GST -> GstTool()
                EverydayTool.TAX -> TaxTool()
                EverydayTool.EMI -> EmiTool()
                EverydayTool.BMI -> BmiTool()
                EverydayTool.PERCENT -> PercentTool()
                EverydayTool.INTEREST -> InterestTool()
                EverydayTool.PROFIT -> ProfitTool()
                EverydayTool.ALGEBRA -> AlgebraTool()
                EverydayTool.BASES -> BasesTool()
                EverydayTool.AGE -> AgeTool()
            }
            Text(
                text = "Results update as you type. Currency values are plain numbers, " +
                    "so they work for ₹, $ or any other unit.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )
        }
    }
}

// ---- shared building blocks -------------------------------------------------

@Composable
private fun NumberInput(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null
) {
    // An explicit type keeps the composable lambda in a slot the compiler can
    // verify (a plain `let` loses the @Composable marker on the way through).
    val suffixSlot: (@Composable () -> Unit)? = if (suffix == null) {
        null
    } else {
        { Text(suffix) }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            // Keep it numeric but allow a decimal point and a leading minus.
            onValueChange(text.filter { it.isDigit() || it == '.' || it == '-' })
        },
        label = { Text(label) },
        singleLine = true,
        suffix = suffixSlot,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier
    )
}

@Composable
private fun ResultRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (emphasis) {
                MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
            },
            color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 3.dp,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

private fun Amount(value: Double, currency: Boolean = true): String =
    (if (currency) "₹ " else "") + Formatter.number(value)

/** Runs [block] for its side-effect of validation and returns any problem text. */
private fun toolError(block: () -> Unit): String? =
    try {
        block()
        null
    } catch (e: MathError) {
        e.message
    } catch (e: Exception) {
        e.message ?: "Check the values"
    }

@Composable
private fun ErrorText(message: String?) {
    if (message == null) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ---- GST --------------------------------------------------------------------

@Composable
private fun GstTool() {
    var amount by remember { mutableStateOf("1000") }
    var rate by remember { mutableStateOf("18") }
    var inclusive by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(amount, "Amount", { amount = it }, Modifier.weight(1f))
            NumberInput(rate, "GST %", { rate = it }, Modifier.weight(1f), suffix = "%")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (inclusive) "Amount already includes GST" else "Add GST to the amount",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = inclusive, onCheckedChange = { inclusive = it })
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("5", "12", "18", "28").forEach { preset ->
                FilterChip(
                    colors = appChipColors(),
                    selected = rate == preset,
                    onClick = { rate = preset },
                    label = { Text("$preset%") }
                )
            }
        }

        val amountValue = amount.toDoubleOrNull()
        val rateValue = rate.toDoubleOrNull()
        if (amountValue == null || rateValue == null) {
            ErrorText("Enter an amount and a GST rate")
        } else {
            val result = runCatching { Everyday.gst(amountValue, rateValue, inclusive) }
            result.fold(
                onSuccess = { gst ->
                    ResultCard {
                        ResultRow("Taxable value", Amount(gst.base))
                        ResultRow("GST @ ${Formatter.number(gst.ratePercent)}%", Amount(gst.gst))
                        ResultRow("CGST (half)", Amount(gst.cgst))
                        ResultRow("SGST (half)", Amount(gst.sgst))
                        ResultRow("Invoice total", Amount(gst.total), emphasis = true)
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }
    }
}

// ---- Income tax -------------------------------------------------------------

@Composable
private fun TaxTool() {
    var gross by remember { mutableStateOf("1300000") }
    var deductions by remember { mutableStateOf("0") }
    var salaried by remember { mutableStateOf(true) }
    var regime by remember { mutableStateOf(Everyday.TaxRegime.NEW) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Everyday.TaxRegime.entries.forEach { entry ->
                FilterChip(
                    colors = appChipColors(),
                    selected = regime == entry,
                    onClick = { regime = entry },
                    label = { Text(entry.label) }
                )
            }
        }
        NumberInput(gross, "Gross annual income", { gross = it })
        NumberInput(deductions, "Deductions (80C, 80D…)", { deductions = it })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Salaried (standard deduction applies)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = salaried, onCheckedChange = { salaried = it })
        }

        val grossValue = gross.toDoubleOrNull()
        val deductionValue = deductions.toDoubleOrNull() ?: 0.0
        if (grossValue == null) {
            ErrorText("Enter the gross annual income")
        } else {
            val result = runCatching {
                Everyday.incomeTax(grossValue, regime, salaried, deductionValue)
            }
            result.fold(
                onSuccess = { tax ->
                    ResultCard {
                        ResultRow("Standard deduction", Amount(tax.standardDeduction))
                        if (tax.otherDeductions > 0) {
                            ResultRow("Other deductions", Amount(tax.otherDeductions))
                        }
                        ResultRow("Taxable income", Amount(tax.taxableIncome))
                        ResultRow("Tax before rebate", Amount(tax.slabTax))
                        if (tax.rebate > 0) ResultRow("Rebate (87A)", "− " + Amount(tax.rebate))
                        if (tax.marginalRelief > 0) {
                            ResultRow("Marginal relief", "− " + Amount(tax.marginalRelief))
                        }
                        ResultRow("Health & education cess (4%)", Amount(tax.cess))
                        ResultRow("Total tax", Amount(tax.totalTax), emphasis = true)
                        ResultRow("Take-home (annual)", Amount(tax.netIncome))
                        ResultRow("Effective rate", "${Formatter.number(tax.effectiveRatePercent)} %")
                        ResultRow("Marginal slab rate", "${Formatter.number(tax.marginalRatePercent)} %")
                    }
                    if (tax.breakdown.isNotEmpty()) {
                        ResultCard {
                            Text(
                                text = "Slab by slab",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            tax.breakdown.forEach { hit ->
                                ResultRow(
                                    "${Formatter.number(hit.from)} – ${Formatter.number(hit.to)}  @ ${Formatter.number(hit.ratePercent)}%",
                                    Amount(hit.tax)
                                )
                            }
                        }
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }
        Text(
            text = "Assumes an individual resident under ${regime.label.lowercase()} for FY 2025-26 " +
                "(AY 2026-27). Surcharge on very high incomes is not included.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---- EMI --------------------------------------------------------------------

@Composable
private fun EmiTool() {
    var principal by remember { mutableStateOf("500000") }
    var rate by remember { mutableStateOf("9") }
    var years by remember { mutableStateOf("5") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberInput(principal, "Loan amount", { principal = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(rate, "Interest rate", { rate = it }, Modifier.weight(1f), suffix = "% p.a.")
            NumberInput(years, "Tenure (years)", { years = it }, Modifier.weight(1f))
        }

        val principalValue = principal.toDoubleOrNull()
        val rateValue = rate.toDoubleOrNull()
        val yearsValue = years.toDoubleOrNull()
        if (principalValue == null || rateValue == null || yearsValue == null) {
            ErrorText("Enter the loan amount, rate and tenure")
        } else {
            val months = Math.round(yearsValue * 12).toInt()
            val result = runCatching { Everyday.emi(principalValue, rateValue, months) }
            result.fold(
                onSuccess = { loan ->
                    ResultCard {
                        ResultRow("Monthly EMI", Amount(loan.emi), emphasis = true)
                        ResultRow("Tenure", "${loan.months} months")
                        ResultRow("Total interest", Amount(loan.totalInterest))
                        ResultRow("Total payable", Amount(loan.totalPayment))
                        ResultRow("Interest as % of loan", "${Formatter.number(loan.interestPercent)} %")
                    }
                    val rows = Everyday.amortisation(loan, limit = 6)
                    if (rows.isNotEmpty()) {
                        ResultCard {
                            Text(
                                text = "First months",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            rows.forEach { row ->
                                ResultRow(
                                    "Month ${row.month}  (balance ${Formatter.number(row.balance)})",
                                    "${Formatter.number(row.interest)} + ${Formatter.number(row.principalPaid)}"
                                )
                            }
                        }
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }
    }
}

// ---- BMI --------------------------------------------------------------------

@Composable
private fun BmiTool() {
    var weight by remember { mutableStateOf("70") }
    var height by remember { mutableStateOf("175") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(weight, "Weight", { weight = it }, Modifier.weight(1f), suffix = "kg")
            NumberInput(height, "Height", { height = it }, Modifier.weight(1f), suffix = "cm")
        }
        val weightValue = weight.toDoubleOrNull()
        val heightValue = height.toDoubleOrNull()
        if (weightValue == null || heightValue == null) {
            ErrorText("Enter a weight and a height")
        } else {
            val result = runCatching { Everyday.bmi(weightValue, heightValue) }
            result.fold(
                onSuccess = { bmi ->
                    ResultCard {
                        ResultRow("BMI", Formatter.number(bmi.bmi), emphasis = true)
                        ResultRow("Category", bmi.category)
                        ResultRow("Healthy weight range", "${Formatter.number(bmi.healthyMinKg)} – ${Formatter.number(bmi.healthyMaxKg)} kg")
                        Text(
                            text = bmi.note,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }
        Text(
            text = "BMI is a rough screening tool, not a diagnosis — athletes and " +
                "pregnant women usually fall outside its assumptions.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---- Percent -----------------------------------------------------------------

@Composable
private fun PercentTool() {
    var a by remember { mutableStateOf("15") }
    var b by remember { mutableStateOf("200") }
    var price by remember { mutableStateOf("2000") }
    var discount by remember { mutableStateOf("10") }
    var extra by remember { mutableStateOf("0") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(a, "X", { a = it }, Modifier.weight(1f))
            NumberInput(b, "Y", { b = it }, Modifier.weight(1f))
        }
        val x = a.toDoubleOrNull()
        val y = b.toDoubleOrNull()
        if (x == null || y == null) {
            ErrorText("Enter two numbers")
        } else {
            val result = runCatching { Everyday.percent(x, y) }
            result.fold(
                onSuccess = { p ->
                    ResultCard {
                        ResultRow("$a% of $b", Formatter.number(p.percentOf))
                        ResultRow("$a is what % of $b", "${Formatter.number(p.whatPercent)} %")
                        ResultRow("From $a to $b", "${Formatter.number(p.changePercent)} % change")
                        ResultRow("$b increased by $a%", Formatter.number(p.increase))
                        ResultRow("$b decreased by $a%", Formatter.number(p.decrease))
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }

        Text("Discount", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(price, "Price", { price = it }, Modifier.weight(1.4f))
            NumberInput(discount, "Off %", { discount = it }, Modifier.weight(1f))
            NumberInput(extra, "Extra %", { extra = it }, Modifier.weight(1f))
        }
        val priceValue = price.toDoubleOrNull()
        val discountValue = discount.toDoubleOrNull()
        val extraValue = extra.toDoubleOrNull() ?: 0.0
        if (priceValue == null || discountValue == null) {
            ErrorText("Enter a price and a discount")
        } else {
            runCatching { Everyday.discount(priceValue, discountValue, extraValue) }.fold(
                onSuccess = { d ->
                    ResultCard {
                        ResultRow("You save", Formatter.number(d.totalDiscount))
                        ResultRow("You pay", Formatter.number(d.payable), emphasis = true)
                        ResultRow("Effective discount", "${Formatter.number(d.effectiveDiscountPercent)} %")
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }
    }
}

// ---- Interest ----------------------------------------------------------------

@Composable
private fun InterestTool() {
    var principal by remember { mutableStateOf("100000") }
    var rate by remember { mutableStateOf("7.5") }
    var years by remember { mutableStateOf("5") }
    var perYear by remember { mutableStateOf("4") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberInput(principal, "Principal", { principal = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(rate, "Rate", { rate = it }, Modifier.weight(1f), suffix = "% p.a.")
            NumberInput(years, "Years", { years = it }, Modifier.weight(1f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text("Compounding", style = MaterialTheme.typography.bodyMedium)
            listOf("1" to "Yearly", "2" to "Half-yearly", "4" to "Quarterly", "12" to "Monthly")
                .forEach { (value, label) ->
                    FilterChip(
                        colors = appChipColors(),
                        selected = perYear == value,
                        onClick = { perYear = value },
                        label = { Text(label) }
                    )
                }
        }

        val principalValue = principal.toDoubleOrNull()
        val rateValue = rate.toDoubleOrNull()
        val yearsValue = years.toDoubleOrNull()
        val perYearValue = perYear.toIntOrNull()
        if (principalValue == null || rateValue == null || yearsValue == null || perYearValue == null) {
            ErrorText("Fill in the amount, rate and duration")
        } else {
            runCatching { Everyday.interest(principalValue, rateValue, yearsValue, perYearValue) }
                .fold(
                    onSuccess = { i ->
                        ResultCard {
                            ResultRow("Simple interest", Amount(i.simpleInterest))
                            ResultRow("Simple total", Amount(i.simpleTotal))
                            ResultRow("Compound interest", Amount(i.compoundInterest), emphasis = true)
                            ResultRow("Compound total", Amount(i.compoundTotal))
                            ResultRow(
                                "Compound advantage",
                                Amount(i.compoundInterest - i.simpleInterest)
                            )
                        }
                    },
                    onFailure = { ErrorText(it.message) }
                )
        }
    }
}

// ---- Profit ------------------------------------------------------------------

@Composable
private fun ProfitTool() {
    var cost by remember { mutableStateOf("80") }
    var selling by remember { mutableStateOf("100") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(cost, "Cost price", { cost = it }, Modifier.weight(1f))
            NumberInput(selling, "Selling price", { selling = it }, Modifier.weight(1f))
        }
        val costValue = cost.toDoubleOrNull()
        val sellingValue = selling.toDoubleOrNull()
        if (costValue == null || sellingValue == null) {
            ErrorText("Enter a cost price and a selling price")
        } else {
            runCatching { Everyday.margin(costValue, sellingValue) }.fold(
                onSuccess = { m ->
                    ResultCard {
                        ResultRow(
                            if (m.profit >= 0) "Profit" else "Loss",
                            Formatter.number(Math.abs(m.profit)),
                            emphasis = true
                        )
                        ResultRow(
                            if (m.profit >= 0) "Profit on cost" else "Loss on cost",
                            "${Formatter.number(Math.abs(m.profitPercent))} %"
                        )
                        ResultRow("Margin on selling price", "${Formatter.number(Math.abs(m.marginPercent))} %")
                        ResultRow("Markup factor", "${Formatter.number(m.markup)}×")
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }
        Text(
            text = "Profit on cost and margin on selling price are different numbers — " +
                "shopkeepers quote whichever looks better.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---- Algebra -----------------------------------------------------------------

@Composable
private fun AlgebraTool() {
    var a by remember { mutableStateOf("1") }
    var b by remember { mutableStateOf("-3") }
    var c by remember { mutableStateOf("2") }
    var number by remember { mutableStateOf("360") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(a, "a", { a = it }, Modifier.weight(1f))
            NumberInput(b, "b", { b = it }, Modifier.weight(1f))
            NumberInput(c, "c", { c = it }, Modifier.weight(1f))
        }
        Text(
            text = "Solves a x² + b x + c = 0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val av = a.toDoubleOrNull()
        val bv = b.toDoubleOrNull()
        val cv = c.toDoubleOrNull()
        if (av == null || bv == null || cv == null) {
            ErrorText("Enter the three coefficients")
        } else {
            runCatching { Everyday.quadratic(av, bv, cv) }.fold(
                onSuccess = { q ->
                    ResultCard {
                        ResultRow("Discriminant", Formatter.number(q.discriminant))
                        ResultRow(
                            if (q.realRoots) "Root 1" else "Root 1 (complex)",
                            Formatter.value(q.root1)
                        )
                        ResultRow(
                            if (q.realRoots) "Root 2" else "Root 2 (complex)",
                            Formatter.value(q.root2)
                        )
                        ResultRow("Vertex", "(${Formatter.number(q.vertexX)}, ${Formatter.number(q.vertexY)})")
                        ResultRow("Sum of roots", Formatter.number(q.sumOfRoots))
                        ResultRow("Product of roots", Formatter.number(q.productOfRoots))
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }

        Text("Prime check and factorisation", style = MaterialTheme.typography.titleSmall)
        NumberInput(number, "Whole number", { number = it })
        val numberValue = number.toLongOrNull()
        if (numberValue == null) {
            ErrorText("Enter a whole number")
        } else {
            ResultCard {
                ResultRow("Prime?", if (Everyday.isPrime(numberValue)) "Yes" else "No")
                val factors = runCatching { Everyday.primeFactors(numberValue) }
                factors.fold(
                    onSuccess = { list ->
                        ResultRow("Prime factors", Everyday.factorString(list))
                        ResultRow("Number of factors", countOfDivisors(list).toString())
                    },
                    onFailure = { ErrorText(it.message) }
                )
            }
        }
    }
}

/** Divisor count comes straight from the prime factorisation exponents. */
private fun countOfDivisors(factors: List<Long>): Long {
    if (factors.isEmpty()) return 0
    return factors.groupingBy { it }.eachCount().values
        .fold(1L) { acc, exponent -> acc * (exponent + 1) }
}

// ---- Bases -------------------------------------------------------------------

@Composable
private fun BasesTool() {
    var value by remember { mutableStateOf("255") }
    var fromBase by remember { mutableStateOf("10") }
    var toBase by remember { mutableStateOf("16") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.uppercase().filter { ch -> ch.isLetterOrDigit() } },
            label = { Text("Value") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(fromBase, "From base", { fromBase = it }, Modifier.weight(1f))
            NumberInput(toBase, "To base", { toBase = it }, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("2", "8", "10", "16", "36").forEach { base ->
                FilterChip(
                    colors = appChipColors(),
                    selected = toBase == base,
                    onClick = { toBase = base },
                    label = { Text("base $base") }
                )
            }
        }

        val from = fromBase.toIntOrNull()
        val to = toBase.toIntOrNull()
        if (from == null || to == null) {
            ErrorText("Enter both bases as numbers between 2 and 36")
        } else {
            runCatching { Everyday.baseConvert(value, from, to) }.fold(
                onSuccess = { converted ->
                    ResultCard {
                        ResultRow("Base $to", converted, emphasis = true)
                        ResultRow("Decimal", runCatching { Everyday.baseConvert(value, from, 10) }.getOrDefault("—"))
                        ResultRow("Binary", runCatching { Everyday.baseConvert(value, from, 2) }.getOrDefault("—"))
                        ResultRow("Hexadecimal", runCatching { Everyday.baseConvert(value, from, 16) }.getOrDefault("—"))
                    }
                },
                onFailure = { ErrorText(it.message) }
            )
        }
    }
}

// ---- Age ---------------------------------------------------------------------

@Composable
private fun AgeTool() {
    var dateOfBirth by remember { mutableStateOf("15/08/1998") }
    var asOn by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = dateOfBirth,
            onValueChange = { dateOfBirth = it },
            label = { Text("Date of birth (dd/mm/yyyy)") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = asOn,
            onValueChange = { asOn = it },
            label = { Text("As on date (blank = today)") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
        )

        val error = toolError { Everyday.parseDate(dateOfBirth) }
        val today = remember { LocalDate.now() }
        if (error != null) {
            ErrorText(error)
        } else {
            val dob = Everyday.parseDate(dateOfBirth)
            val target = if (asOn.isBlank()) today else runCatching { Everyday.parseDate(asOn) }.getOrNull()
            if (target == null) {
                ErrorText("The 'as on' date is not valid")
            } else {
                runCatching { Everyday.age(dob, target) }.fold(
                    onSuccess = { age ->
                        ResultCard {
                            ResultRow("Age", "${age.years} years ${age.months} months ${age.days} days", emphasis = true)
                            ResultRow("Total months", "${age.totalMonths}")
                            ResultRow("Total weeks", "${age.totalWeeks}")
                            ResultRow("Total days", "${age.totalDays}")
                            ResultRow("Next birthday in", "${age.nextBirthdayInDays} days")
                        }
                    },
                    onFailure = { ErrorText(it.message) }
                )
            }
        }
    }
}
