package com.tdater.blackbox.core.templates

import com.tdater.blackbox.core.model.ProjectCategory

/**
 * Starter kits that make BLACKBOX fit more than app debugging. A template only seeds a new project:
 * its condition fields, suggested readings and example problems. Nothing about it is enforced later.
 */
data class ProjectTemplate(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val category: ProjectCategory,
    /** Condition fields recorded in snapshots ("what was true when this happened"). */
    val fields: List<String>,
    /** Readings worth logging over time, with a default unit ("" = no unit). */
    val readings: List<Pair<String, String>> = emptyList(),
    val exampleProblems: List<String> = emptyList(),
    val titleHint: String = "",
    /** ARGB accent used for the project's avatar. */
    val accent: Long,
    /** Shows the build/version section. Off for things that don't have builds. */
    val tracksBuilds: Boolean = false,
    val note: String? = null,
)

object ProjectTemplates {
    const val CUSTOM = "custom"

    val all: List<ProjectTemplate> = listOf(
        ProjectTemplate(
            "vehicle", "Vehicle", "🚗", "Weird noises, warning lights, fault codes",
            ProjectCategory.PHYSICAL,
            listOf("Make & model", "Year", "Mileage", "Engine", "Fault codes", "Fuel level", "Outside temperature", "Last service"),
            listOf("Battery voltage" to "V", "Mileage" to "mi", "Fuel economy" to "mpg", "Tire pressure" to "psi", "Coolant temperature" to "°F"),
            listOf("Check engine light after cold start", "Clunk over bumps on the left side", "Battery dead after two days parked"),
            titleHint = "2014 F-150", accent = 0xFFFF6B1A, tracksBuilds = false,
        ),
        ProjectTemplate(
            "home", "Home & repair", "🏠", "Appliances, plumbing, electrical, HVAC",
            ProjectCategory.PHYSICAL,
            listOf("Room / location", "Appliance or fixture", "Brand & model", "Age", "Power / breaker", "Last serviced", "Weather"),
            listOf("Temperature" to "°F", "Humidity" to "%", "Water pressure" to "psi", "Voltage" to "V"),
            listOf("Dishwasher leaves dishes wet", "Breaker trips when the microwave runs", "Upstairs never gets cool"),
            titleHint = "Upstairs HVAC", accent = 0xFF4BE38A,
        ),
        ProjectTemplate(
            "network", "Network & homelab", "🖧", "Servers, Wi‑Fi, NAS, routers, self-hosting",
            ProjectCategory.SOFTWARE,
            listOf("Host / device", "OS / firmware", "IP address", "Connection (wired / Wi‑Fi)", "Router / switch", "ISP", "Uptime", "Services affected"),
            listOf("Latency" to "ms", "Download speed" to "Mbps", "Upload speed" to "Mbps", "Packet loss" to "%", "CPU temperature" to "°C"),
            listOf("Plex buffers every evening", "NAS drops off the network overnight", "Wi‑Fi dead zone in the garage"),
            titleHint = "Homelab", accent = 0xFF3FE0D0, tracksBuilds = true,
        ),
        ProjectTemplate(
            "software", "App or software", "📱", "Apps, devices, firmware, your own code",
            ProjectCategory.SOFTWARE,
            listOf("Device", "OS version", "App version", "Connection mode", "Battery saver", "Account / build type"),
            listOf("Startup time" to "ms", "Memory" to "MB", "Battery drain" to "%/h"),
            listOf("Crash after screen rotation", "Values freeze after screen-off", "Login loop on Wi‑Fi only"),
            titleHint = "KITT launcher", accent = 0xFF8B7CFF, tracksBuilds = true,
        ),
        ProjectTemplate(
            "making", "3D printing & making", "🖨️", "Printers, CNC, electronics, woodworking",
            ProjectCategory.PHYSICAL,
            listOf("Machine", "Firmware", "Material / filament", "Nozzle or tool", "Slicer / profile", "Room temperature"),
            listOf("Nozzle temperature" to "°C", "Bed temperature" to "°C", "Layer height" to "mm", "Print speed" to "mm/s"),
            listOf("First layer won't stick", "Stringing on PETG", "Layer shift halfway up"),
            titleHint = "Ender 3 V2", accent = 0xFFFFC23D, tracksBuilds = true,
        ),
        ProjectTemplate(
            "garden", "Plants & garden", "🌱", "Houseplants, beds, lawns, aquariums",
            ProjectCategory.PHYSICAL,
            listOf("Plant / area", "Light", "Soil / medium", "Watering schedule", "Fertilizer", "Pot / container", "Season"),
            listOf("Soil moisture" to "%", "Temperature" to "°F", "Humidity" to "%", "pH" to ""),
            listOf("Fiddle leaf dropping leaves", "Tomatoes splitting", "Brown patches in the lawn"),
            titleHint = "Living room plants", accent = 0xFF7BD389,
        ),
        ProjectTemplate(
            "health", "Health & habits", "❤️", "Symptoms, sleep, food, routines",
            ProjectCategory.OTHER,
            listOf("Time of day", "Sleep last night", "Food & drink", "Medication", "Activity", "Stress", "Weather"),
            listOf("Severity" to "/10", "Sleep" to "h", "Weight" to "lb", "Blood pressure (systolic)" to "mmHg", "Steps" to ""),
            listOf("Headaches in the afternoon", "Knee pain after runs", "Waking up at 3 a.m."),
            titleHint = "Afternoon headaches", accent = 0xFFFF5A8A,
            note = "A personal log, not medical advice. Bring what you record to a professional.",
        ),
        ProjectTemplate(
            "kitchen", "Cooking & recipes", "🍳", "Recipes you're dialing in, bread, coffee, smoking",
            ProjectCategory.COMPARISON,
            listOf("Recipe", "Key ingredients", "Equipment", "Oven / heat setting", "Altitude", "Humidity"),
            listOf("Temperature" to "°F", "Time" to "min", "Weight" to "g", "Rating" to "/10"),
            listOf("Sourdough comes out dense", "Brisket stalls forever", "Espresso tastes sour"),
            titleHint = "Sourdough", accent = 0xFFFF9A3D,
        ),
        ProjectTemplate(
            CUSTOM, "Something else", "✏️", "Start blank and add your own fields",
            ProjectCategory.OTHER, emptyList(), emptyList(), emptyList(), accent = 0xFFB4B9C8, tracksBuilds = true,
        ),
    )

    fun byId(id: String?): ProjectTemplate = all.firstOrNull { it.id == id } ?: all.first { it.id == CUSTOM }
}
