package com.wms.ai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Locks the sealed-port pattern: an {@code internal} package is the module's
 * private implementation, unreachable from any other module at compile time.
 * This is the automated guard for README §2's Feature + sealed-port principle.
 */
@AnalyzeClasses(packages = "com.wms.ai", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule inventoryInternalsAreNotReferencedFromOutsideTheModule =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.wms.ai.inventory..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.wms.ai.inventory.internal..")
                    .because("internal/ types are the module's private implementation; "
                            + "callers must depend only on the InventoryService port and Stock");
}
