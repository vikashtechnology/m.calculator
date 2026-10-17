package com.advanceai.calculator.data

/** Bundled About content. This object deliberately has no persistence or network access. */
data class AboutPage(
    val title: String,
    val tagline: String,
    val body: List<String>,
    val links: List<Link>,
    val contact: String = ""
) {
    data class Link(val label: String, val url: String)

    companion object {
        val Local = AboutPage(
            title = "AI Calculator",
            tagline = "A calculator that does the maths a phone calculator usually cannot.",
            body = listOf(
                "Graphing with pan, zoom and trace, equation solving, calculus, matrices, unit conversion, statistics, and everyday calculators.",
                "The calculator runs on your phone. Ask AI and advertising connect to their selected providers only when those features are used; About and donation details are bundled locally. No account or usage telemetry is used."
            ),
            links = listOf( Link("GitHub profile", "https://github.com/vikkiRoy899") )
        )
    }
}
