package com.advanceai.calculator.engine

/** Categories shown as tabs in the unit converter. */
enum class UnitCategory(val label: String) {
    LENGTH("Length"),
    MASS("Mass"),
    TEMPERATURE("Temp"),
    AREA("Area"),
    VOLUME("Volume"),
    SPEED("Speed"),
    TIME("Time"),
    DATA("Data"),
    ANGLE("Angle"),
    ENERGY("Energy"),
    PRESSURE("Pressure")
}

/**
 * A unit is converted to its category base unit with `base = value * factor + offset`.
 * Only temperatures need a non-zero offset.
 */
data class UnitDef(
    val id: String,
    val name: String,
    val symbol: String,
    val category: UnitCategory,
    val factor: Double,
    val offset: Double = 0.0
)

object Units {

    private fun u(
        id: String,
        name: String,
        symbol: String,
        category: UnitCategory,
        factor: Double,
        offset: Double = 0.0
    ) = UnitDef(id, name, symbol, category, factor, offset)

    val all: List<UnitDef> = listOf(
        // Length (base: metre)
        u("m", "Meter", "m", UnitCategory.LENGTH, 1.0),
        u("km", "Kilometer", "km", UnitCategory.LENGTH, 1000.0),
        u("cm", "Centimeter", "cm", UnitCategory.LENGTH, 0.01),
        u("mm", "Millimeter", "mm", UnitCategory.LENGTH, 0.001),
        u("um", "Micrometer", "µm", UnitCategory.LENGTH, 1e-6),
        u("nm", "Nanometer", "nm", UnitCategory.LENGTH, 1e-9),
        u("in", "Inch", "in", UnitCategory.LENGTH, 0.0254),
        u("ft", "Foot", "ft", UnitCategory.LENGTH, 0.3048),
        u("yd", "Yard", "yd", UnitCategory.LENGTH, 0.9144),
        u("mi", "Mile", "mi", UnitCategory.LENGTH, 1609.344),
        u("nmi", "Nautical mile", "nmi", UnitCategory.LENGTH, 1852.0),
        u("ly", "Light year", "ly", UnitCategory.LENGTH, 9.4607304725808e15),

        // Mass (base: kilogram)
        u("kg", "Kilogram", "kg", UnitCategory.MASS, 1.0),
        u("g", "Gram", "g", UnitCategory.MASS, 0.001),
        u("mg", "Milligram", "mg", UnitCategory.MASS, 1e-6),
        u("t", "Tonne", "t", UnitCategory.MASS, 1000.0),
        u("lb", "Pound", "lb", UnitCategory.MASS, 0.45359237),
        u("oz", "Ounce", "oz", UnitCategory.MASS, 0.028349523125),
        u("st", "Stone", "st", UnitCategory.MASS, 6.35029318),

        // Temperature (base: kelvin)
        u("degC", "Celsius", "°C", UnitCategory.TEMPERATURE, 1.0, 273.15),
        u("degF", "Fahrenheit", "°F", UnitCategory.TEMPERATURE, 5.0 / 9.0, 255.3722222222222),
        u("K", "Kelvin", "K", UnitCategory.TEMPERATURE, 1.0),
        u("degR", "Rankine", "°R", UnitCategory.TEMPERATURE, 5.0 / 9.0),

        // Area (base: square metre)
        u("m2", "Square meter", "m²", UnitCategory.AREA, 1.0),
        u("km2", "Square kilometer", "km²", UnitCategory.AREA, 1e6),
        u("cm2", "Square centimeter", "cm²", UnitCategory.AREA, 1e-4),
        u("ha", "Hectare", "ha", UnitCategory.AREA, 10000.0),
        u("acre", "Acre", "acre", UnitCategory.AREA, 4046.8564224),
        u("ft2", "Square foot", "ft²", UnitCategory.AREA, 0.09290304),
        u("mi2", "Square mile", "mi²", UnitCategory.AREA, 2589988.110336),

        // Volume (base: litre)
        u("L", "Liter", "L", UnitCategory.VOLUME, 1.0),
        u("mL", "Milliliter", "mL", UnitCategory.VOLUME, 0.001),
        u("m3", "Cubic meter", "m³", UnitCategory.VOLUME, 1000.0),
        u("cm3", "Cubic centimeter", "cm³", UnitCategory.VOLUME, 0.001),
        u("gal", "US gallon", "gal", UnitCategory.VOLUME, 3.785411784),
        u("qt", "US quart", "qt", UnitCategory.VOLUME, 0.946352946),
        u("pt", "US pint", "pt", UnitCategory.VOLUME, 0.473176473),
        u("cup", "US cup", "cup", UnitCategory.VOLUME, 0.2365882365),
        u("floz", "US fluid ounce", "fl oz", UnitCategory.VOLUME, 0.0295735295625),
        u("tbsp", "Tablespoon", "tbsp", UnitCategory.VOLUME, 0.01478676478125),
        u("tsp", "Teaspoon", "tsp", UnitCategory.VOLUME, 0.00492892159375),
        u("galUK", "UK gallon", "gal (UK)", UnitCategory.VOLUME, 4.54609),

        // Speed (base: metre per second)
        u("mps", "Meter/second", "m/s", UnitCategory.SPEED, 1.0),
        u("kph", "Kilometer/hour", "km/h", UnitCategory.SPEED, 1.0 / 3.6),
        u("mph", "Mile/hour", "mph", UnitCategory.SPEED, 0.44704),
        u("knot", "Knot", "kn", UnitCategory.SPEED, 0.5144444444444444),
        u("fps", "Foot/second", "ft/s", UnitCategory.SPEED, 0.3048),
        u("c", "Speed of light", "c", UnitCategory.SPEED, 299792458.0),

        // Time (base: second)
        u("s", "Second", "s", UnitCategory.TIME, 1.0),
        u("ms", "Millisecond", "ms", UnitCategory.TIME, 0.001),
        u("us", "Microsecond", "µs", UnitCategory.TIME, 1e-6),
        u("min", "Minute", "min", UnitCategory.TIME, 60.0),
        u("h", "Hour", "h", UnitCategory.TIME, 3600.0),
        u("day", "Day", "d", UnitCategory.TIME, 86400.0),
        u("week", "Week", "wk", UnitCategory.TIME, 604800.0),
        u("year", "Year (365 d)", "yr", UnitCategory.TIME, 31536000.0),

        // Data (base: byte)
        u("bit", "Bit", "bit", UnitCategory.DATA, 0.125),
        u("B", "Byte", "B", UnitCategory.DATA, 1.0),
        u("KB", "Kilobyte", "KB", UnitCategory.DATA, 1000.0),
        u("KiB", "Kibibyte", "KiB", UnitCategory.DATA, 1024.0),
        u("MB", "Megabyte", "MB", UnitCategory.DATA, 1e6),
        u("MiB", "Mebibyte", "MiB", UnitCategory.DATA, 1048576.0),
        u("GB", "Gigabyte", "GB", UnitCategory.DATA, 1e9),
        u("GiB", "Gibibyte", "GiB", UnitCategory.DATA, 1073741824.0),
        u("TB", "Terabyte", "TB", UnitCategory.DATA, 1e12),

        // Angle (base: radian)
        u("rad", "Radian", "rad", UnitCategory.ANGLE, 1.0),
        u("deg", "Degree", "°", UnitCategory.ANGLE, Math.PI / 180.0),
        u("grad", "Gradian", "grad", UnitCategory.ANGLE, Math.PI / 200.0),
        u("turn", "Turn", "turn", UnitCategory.ANGLE, 2 * Math.PI),
        u("arcmin", "Arcminute", "′", UnitCategory.ANGLE, Math.PI / 10800.0),
        u("arcsec", "Arcsecond", "″", UnitCategory.ANGLE, Math.PI / 648000.0),

        // Energy (base: joule)
        u("J", "Joule", "J", UnitCategory.ENERGY, 1.0),
        u("kJ", "Kilojoule", "kJ", UnitCategory.ENERGY, 1000.0),
        u("cal", "Calorie", "cal", UnitCategory.ENERGY, 4.184),
        u("kcal", "Kilocalorie", "kcal", UnitCategory.ENERGY, 4184.0),
        u("Wh", "Watt-hour", "Wh", UnitCategory.ENERGY, 3600.0),
        u("kWh", "Kilowatt-hour", "kWh", UnitCategory.ENERGY, 3.6e6),
        u("eV", "Electronvolt", "eV", UnitCategory.ENERGY, 1.602176634e-19),
        u("BTU", "British thermal unit", "BTU", UnitCategory.ENERGY, 1055.05585262),
        u("ftlb", "Foot-pound", "ft·lb", UnitCategory.ENERGY, 1.3558179483314004),

        // Pressure (base: pascal)
        u("Pa", "Pascal", "Pa", UnitCategory.PRESSURE, 1.0),
        u("kPa", "Kilopascal", "kPa", UnitCategory.PRESSURE, 1000.0),
        u("bar", "Bar", "bar", UnitCategory.PRESSURE, 100000.0),
        u("mbar", "Millibar", "mbar", UnitCategory.PRESSURE, 100.0),
        u("atm", "Atmosphere", "atm", UnitCategory.PRESSURE, 101325.0),
        u("psi", "Pound per sq inch", "psi", UnitCategory.PRESSURE, 6894.757293168361),
        u("mmHg", "Millimeter of mercury", "mmHg", UnitCategory.PRESSURE, 133.322387415),
        u("torr", "Torr", "Torr", UnitCategory.PRESSURE, 101325.0 / 760.0)
    )

    fun byCategory(category: UnitCategory): List<UnitDef> = all.filter { it.category == category }

    fun convert(value: Double, from: UnitDef, to: UnitDef): Double {
        require(from.category == to.category) { "Cannot convert ${from.category} to ${to.category}" }
        val base = value * from.factor + from.offset
        return (base - to.offset) / to.factor
    }

    fun find(id: String): UnitDef? = all.firstOrNull { it.id == id }

    /** Parses something like `12 km to mi`, `5'10" in cm`-style input is not supported. */
    fun parseRequest(input: String): ConversionRequest? {
        val cleaned = input.trim().replace("→", "to", ignoreCase = true)
        val separators = listOf(" to ", " in ", " as ", "=")
        // Split on the last separator so "convert 10 in to cm" style text still works.
        for (sep in separators) {
            val index = cleaned.lastIndexOf(sep, ignoreCase = true)
            if (index <= 0 || index + sep.length >= cleaned.length) continue
            val left = cleaned.substring(0, index).trim()
            val right = cleaned.substring(index + sep.length).trim()
            val from = matchUnitSuffix(left) ?: continue
            val to = matchUnit(right) ?: continue
            val numberText = left.substring(0, left.length - from.second.length).trim()
            val number = numberText.toDoubleOrNull() ?: continue
            return ConversionRequest(number, from.first, to)
        }
        return null
    }

    private fun matchUnitSuffix(text: String): Pair<UnitDef, String>? {
        // Prefer the longest suffix match ("km" before "m").
        return all.map { it to it.symbol }
            .filter { (_, symbol) -> text.lowercase().endsWith(symbol.lowercase()) }
            .maxByOrNull { it.second.length }
            ?.let { (unit, symbol) -> unit to symbol }
    }

    private fun matchUnit(text: String): UnitDef? {
        val t = text.trim().lowercase()
        return all.firstOrNull { it.symbol.equals(t, ignoreCase = true) }
            ?: all.firstOrNull { it.id.equals(t, ignoreCase = true) }
            ?: all.firstOrNull { it.name.equals(t, ignoreCase = true) }
    }
}

data class ConversionRequest(val value: Double, val from: UnitDef, val to: UnitDef)
