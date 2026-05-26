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

    @ArchTest
    static final ArchRule orderInternalsAreNotReferencedFromOutsideTheModule =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.wms.ai.order..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.wms.ai.order.internal..")
                    .because("internal/ types are the module's private implementation; callers "
                            + "must depend only on the OrderService port and the Order/OrderItem/"
                            + "NewOrder views");

    @ArchTest
    static final ArchRule outboundInternalsAreNotReferencedFromOutsideTheModule =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.wms.ai.outbound..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.wms.ai.outbound.internal..")
                    .because("internal/ types are the module's private implementation; callers "
                            + "must depend only on the OutboundService port and the Worker/"
                            + "PickingTask views");

    @ArchTest
    static final ArchRule coordinatorInternalsAreNotReferencedFromOutsideTheModule =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.wms.ai.coordinator..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.wms.ai.coordinator.internal..")
                    .because("internal/ types are the module's private implementation; callers "
                            + "must depend only on the DispatchService port and the WarehouseState/"
                            + "DispatchResult views");

    /**
     * The coordinator is the one place that "calls down" into the three business
     * modules' ports; that edge must stay one-directional. No business module may
     * depend on the coordinator (nor, by extension, anything above it), so the modules
     * remain ignorant of who orchestrates them (README §2).
     */
    @ArchTest
    static final ArchRule businessModulesDoNotDependOnTheCoordinator =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "com.wms.ai.inventory..",
                            "com.wms.ai.order..",
                            "com.wms.ai.outbound..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.wms.ai.coordinator..")
                    .because("the coordinator → business-module-ports dependency is one-directional; "
                            + "business modules must stay ignorant of who orchestrates them");
}
