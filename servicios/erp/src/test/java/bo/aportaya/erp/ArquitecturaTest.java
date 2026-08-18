package bo.aportaya.erp;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * La direccion de dependencia no se pide: se verifica (ADR-023).
 *
 * Lo que en una configuracion de lint se eludia con un import creativo, aca es
 * una prueba que falla — y cubre las clases que se escriban dentro de seis
 * semanas, cuando ya nadie se acuerde de esta lista.
 */
@AnalyzeClasses(packages = "bo.aportaya.erp")
class ArquitecturaTest {

    @ArchTest
    static final ArchRule elDominioEsPuro =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..infraestructura..", "..web..",
                "org.springframework..", "org.jooq..");

    @ArchTest
    static final ArchRule laTransaccionVivEnElOrganismo =
        noClasses().that().resideOutsideOfPackage("..aplicacion..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    @ArchTest
    static final ArchRule ningunImportCruzado =
        noClasses().that().resideInAPackage("bo.aportaya.erp..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("bo.aportaya..")
            .andShould().dependOnClassesThat()
            .resideOutsideOfPackages("bo.aportaya.erp..", "bo.aportaya.plataforma..");

    @ArchTest
    static final ArchRule jpaProhibido =
        noClasses().should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    @ArchTest
    static final ArchRule laWebNoTieneReglas =
        noClasses().that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..infraestructura..");
}
