package org.vliux.orca.ui.vehicle

import java.text.Normalizer
import java.util.Locale

internal data class VehicleMakeCatalog(
    val make: String,
    val models: List<String>,
)

private val vehicleMakeCatalogs = listOf(
    VehicleMakeCatalog("Abarth", emptyList()),
    VehicleMakeCatalog("Alfa Romeo", emptyList()),
    VehicleMakeCatalog("Alpine", emptyList()),
    VehicleMakeCatalog("Audi", listOf("A3", "A4", "A5", "A6", "Q3", "Q5", "Q7", "Q8", "e-tron")),
    VehicleMakeCatalog("BMW", listOf("2 Series", "3 Series", "4 Series", "5 Series", "7 Series", "X1", "X3", "X5", "X7", "i4", "i5", "iX")),
    VehicleMakeCatalog("BYD", listOf("Atto 3", "Dolphin", "Han", "Seal", "Seal U", "Tang")),
    VehicleMakeCatalog("Chery", emptyList()),
    VehicleMakeCatalog("Citroen", listOf("Berlingo", "C3", "C3 Aircross", "C4", "C5 Aircross")),
    VehicleMakeCatalog("Corvette", emptyList()),
    VehicleMakeCatalog("CUPRA", listOf("Ateca", "Born", "Formentor", "Leon", "Tavascan", "Terramar")),
    VehicleMakeCatalog("Dacia", listOf("Duster", "Jogger", "Sandero", "Spring")),
    VehicleMakeCatalog("DS", emptyList()),
    VehicleMakeCatalog("Fiat", listOf("500", "500e", "600", "Panda", "Tipo")),
    VehicleMakeCatalog("Ford", listOf("Bronco", "Escape", "Explorer", "F-150", "Fiesta", "Focus", "Fusion", "Mustang", "Ranger", "Transit")),
    VehicleMakeCatalog("Geely", emptyList()),
    VehicleMakeCatalog("Genesis", listOf("Electrified G80", "Electrified GV70", "G70", "G80", "G90", "GV60", "GV70", "GV80")),
    VehicleMakeCatalog("GWM", emptyList()),
    VehicleMakeCatalog("Honda", listOf("Accord", "Civic", "CR-V", "HR-V", "Odyssey", "Passport", "Pilot", "Ridgeline")),
    VehicleMakeCatalog("Hyundai", listOf("Elantra", "Ioniq 5", "Ioniq 6", "Kona", "Palisade", "Santa Fe", "Sonata", "Tucson", "Venue")),
    VehicleMakeCatalog("JAECOO", emptyList()),
    VehicleMakeCatalog("Jaguar", listOf("E-PACE", "F-PACE", "F-TYPE", "I-PACE", "XE", "XF")),
    VehicleMakeCatalog("Jeep", listOf("Cherokee", "Compass", "Gladiator", "Grand Cherokee", "Renegade", "Wagoneer", "Wrangler")),
    VehicleMakeCatalog("Kia", listOf("EV6", "Forte", "K5", "Niro", "Seltos", "Sorento", "Soul", "Sportage", "Telluride")),
    VehicleMakeCatalog("Land Rover", listOf("Defender", "Discovery", "Discovery Sport", "Range Rover", "Range Rover Evoque", "Range Rover Sport", "Range Rover Velar")),
    VehicleMakeCatalog("Leapmotor", emptyList()),
    VehicleMakeCatalog("Lexus", listOf("ES", "GX", "IS", "LC", "LS", "LX", "NX", "RX", "TX", "UX")),
    VehicleMakeCatalog("Maserati", emptyList()),
    VehicleMakeCatalog("Mazda", listOf("CX-30", "CX-5", "CX-50", "CX-9", "CX-90", "Mazda3", "Mazda6", "MX-5 Miata")),
    VehicleMakeCatalog("Mercedes-Benz", listOf("A-Class", "C-Class", "E-Class", "GLA", "GLB", "GLC", "GLE", "GLS", "S-Class")),
    VehicleMakeCatalog("MG", listOf("MG3", "MG4", "MG5", "HS", "ZS")),
    VehicleMakeCatalog("MINI", listOf("Clubman", "Convertible", "Cooper", "Countryman", "Hardtop")),
    VehicleMakeCatalog("Mitsubishi", listOf("Eclipse Cross", "Mirage", "Outlander", "Outlander PHEV")),
    VehicleMakeCatalog("Nissan", listOf("Altima", "Ariya", "Frontier", "Kicks", "Leaf", "Murano", "Pathfinder", "Rogue", "Sentra", "Versa")),
    VehicleMakeCatalog("Omoda", emptyList()),
    VehicleMakeCatalog("Peugeot", listOf("208", "2008", "308", "3008", "408", "5008")),
    VehicleMakeCatalog("Polestar", listOf("2", "3", "4")),
    VehicleMakeCatalog("Porsche", listOf("718 Boxster", "718 Cayman", "911", "Cayenne", "Macan", "Panamera", "Taycan")),
    VehicleMakeCatalog("Renault", listOf("Captur", "Clio", "Megane", "Scenic", "Symbioz")),
    VehicleMakeCatalog("SEAT", listOf("Arona", "Ateca", "Ibiza", "Leon", "Tarraco")),
    VehicleMakeCatalog("Skoda", listOf("Fabia", "Kamiq", "Karoq", "Kodiaq", "Octavia", "Scala", "Superb")),
    VehicleMakeCatalog("Smart", emptyList()),
    VehicleMakeCatalog("SsangYong", emptyList()),
    VehicleMakeCatalog("Subaru", listOf("Ascent", "Crosstrek", "Forester", "Impreza", "Legacy", "Outback", "WRX")),
    VehicleMakeCatalog("Tesla", listOf("Cybertruck", "Model 3", "Model S", "Model X", "Model Y")),
    VehicleMakeCatalog("Toyota", listOf("4Runner", "Avalon", "Camry", "Corolla", "Highlander", "Prius", "RAV4", "Sequoia", "Sienna", "Tacoma", "Tundra", "Venza", "Yaris")),
    VehicleMakeCatalog("Vauxhall", listOf("Astra", "Corsa", "Crossland", "Grandland", "Mokka")),
    VehicleMakeCatalog("Volkswagen", listOf("Atlas", "Golf", "GTI", "ID.4", "Jetta", "Passat", "Taos", "Tiguan")),
    VehicleMakeCatalog("Volvo", listOf("C40", "EX30", "EX90", "S60", "S90", "XC40", "XC60", "XC90")),
)

private val vehicleMakeCatalogByKey = vehicleMakeCatalogs.associateBy { it.make.catalogKey() }

internal fun knownVehicleMakes(): List<String> = vehicleMakeCatalogs.map(VehicleMakeCatalog::make)

internal fun findVehicleMakeCatalog(make: String): VehicleMakeCatalog? = vehicleMakeCatalogByKey[make.catalogKey()]

internal fun hasCatalogModels(make: String): Boolean = !findVehicleMakeCatalog(make)?.models.isNullOrEmpty()

private fun String.catalogKey(): String {
    val normalized = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    return buildString(normalized.length) {
        normalized.forEach { character ->
            if (Character.getType(character) != Character.NON_SPACING_MARK.toInt()) {
                append(character)
            }
        }
    }
        .lowercase(Locale.US)
}
