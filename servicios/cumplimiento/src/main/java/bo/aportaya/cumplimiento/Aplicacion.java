package bo.aportaya.cumplimiento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * El punto de arranque de cumplimiento. No tiene logica: si aparece un if sobre una
 * regla del pasanaku aca, esta mal ubicado — va a aplicacion/.
 *
 * La configuracion se valida al arrancar: si falta una clave, el proceso NO levanta
 * y dice cual (planes/01 §0.7).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Aplicacion {

    public static void main(String[] argumentos) {
        SpringApplication.run(Aplicacion.class, argumentos);
    }
}
