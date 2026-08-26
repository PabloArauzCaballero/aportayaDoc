package bo.aportaya.notificaciones;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * La direccion de dependencia no se pide: se verifica (ADR-023).
 *
 * Lo que en una configuracion de lint se eludia con un import creativo, aca es
 * una prueba que falla — y cubre las clases que se escriban dentro de seis
 * semanas, cuando ya nadie se acuerde de esta lista.
 */
@AnalyzeClasses(packages = "bo.aportaya.notificaciones")
class ArquitecturaTest {

    @ArchTest
    static final ArchRule elDominioEsPuro = noClasses()
            .that()
            .resideInAPackage("..dominio..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..infraestructura..", "..web..",
                    "org.springframework..", "org.jooq..");

    @ArchTest
    static final ArchRule laTransaccionVivEnElOrganismo = noClasses()
            .that()
            .resideOutsideOfPackage("..aplicacion..")
            .should()
            .beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    // Invariante 11: se depende del propio servicio y de plataforma/. De ningun
    // otro. El predicado va COMPUESTO y no en dos `should` encadenados: encadenados,
    // java.lang.Object alcanza para que cualquier clase viole la regla.
    @ArchTest
    static final ArchRule ningunImportCruzado = noClasses()
            .that()
            .resideInAPackage("bo.aportaya.notificaciones..")
            .should()
            .dependOnClassesThat(resideInAPackage("bo.aportaya..")
                    .and(resideOutsideOfPackages("bo.aportaya.notificaciones..", "bo.aportaya.plataforma..")));

    @ArchTest
    static final ArchRule jpaProhibido =
            noClasses().should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    @ArchTest
    static final ArchRule laWebNoTieneReglas = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infraestructura..");
}
