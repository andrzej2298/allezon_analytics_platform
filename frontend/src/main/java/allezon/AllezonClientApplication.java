package allezon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AllezonClientApplication {

    private static final Logger log = LoggerFactory.getLogger(AllezonClientApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AllezonClientApplication.class, args);
    }

}
