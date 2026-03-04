package org.example.mcpclient;

import org.example.mcpclient.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class McpClientApplication {
    private static final Logger LOG =
            LoggerFactory.getLogger(McpClientApplication.class);

    public McpClientApplication(ChatService chatService) {
        this.chatService = chatService;
    }

    public static void main(String[] args) {
        SpringApplication.run(McpClientApplication.class, args);
    }

    private final ChatService chatService;

    /*@Bean
    public CommandLineRunner scenarioLLM(ChatClient.Builder chatClientBuilder,
                                         ToolCallbackProvider tools,
                                         ConfigurableApplicationContext context) {

        return args -> {
            String input = assignmentInput(0);
            //String input = consultantInput(7);
            //String input = serviceInput(5);
            //String input = customerInput(3);
            //String input = organisationInput(7);
            //String input = otherInput(0);

            ChatRequest chatRequest = new ChatRequest(input, "");
            String answer = chatService.chat(chatRequest);

            context.close();
        };
    }*/

    // ============================================================
    // TEST INPUTS
    // ============================================================

    private String assignmentInput(int index) {
        String[] input = Arrays.asList(
                "Föreslå konsulter till uppdraget ASSIGN_300009",
                "Vilka uppdrag är i status NO_SHOW?",
                "Hur många uppdrag är i NO_SHOW?",
                "Vilka uppdrag finns i följande datum 2026-02-23?",
                "Hur många uppdrag finns i följande datum 2026-02-23?",
                "Vilka konsulter är på uppdrag datum 2026-02-23?",
                "Jobbar Lovisa Wallin den 2026-02-23?"
                //"Skapa ett nytt uppdrag för kund CUST_WAREHOUSE_1 som har följande startdatum 2026-03-31 07:00 och slutdatum 2026-03-31 16:00 samt kräver kompetens ForkliftOperator.",
                //"Tilldela konsult Karin Lundqvist, CONS_100086 till uppdraget ASSIGN_300018.",
                //"Föreslå 3 konsulter för uppdraget ASSIGN_300018?"
        ).toArray(String[]::new);

        return input[index];
    }

    private String consultantInput(int index) {
        String[] input = Arrays.asList(
                "Lista alla konsulter.",
                "Visa uppgifter om konsult CONS_100001?",
                "Visa uppgifter om konsult CONS_100002?",
                "Visa uppgifter om konsult Karin Håkansson?",
                "Visa konsultprofil för Karin Lundqvist, CONS_100086.",
                "Vilka konsulter är tillgängliga den 2026-02-20?",
                "Vilka konsulter är tillgängliga den 2026-02-23?",
                "Lista konsulter som är sjuka den 2026-02-25."
                //"Lägg till en anteckning som säger \"Hon är duktig!\" på konsult Karin Lundqvist, CONS_100086.",
                //"Uppdatera tillgänglighet för Karin Lundqvist, CONS_100086. Ny tid är hon kan är startdatum 2026-03-31 07:00 och slutdatum 2026-03-31 16:00."
        ).toArray(String[]::new);

        return input[index];
    }

    private String serviceInput(int index) {
        String[] input = Arrays.asList(
                "Vilka kompetenser har konsult CONS_100086?",
                "Vilka kompetenser har konsult Karin Håkansson?",
                "Vilka kompetenser har konsult Karin Lundqvist?",
                "Vilka konsulter har kompetensen Picker och TeamLead?",
                //"Vilka konsulter har Picker och TeamLead?",
                //"Vilka konsulter har ForkliftOperator?",
                "Vilka konsulter har kompetensen ForkliftOperator?",
                "Lista alla kompetenser."
                //"Skapa en ny kompetens som heter AI-expert.",
                //"Ta bort kompetensen AI-expert."
        ).toArray(String[]::new);

        return input[index];
    }

    private String customerInput(int index) {
        String[] input = Arrays.asList(
                "Lista alla kunder.",
                "Visa information om kund CUST_WAREHOUSE_10.",
                "Visa alla kunder med riskprofil MEDIUM.",
                "Visa alla kunder i region malmö."
                //"Skapa en ny kund som heter UPS och som har kundid CUST_WAREHOUSE_11.",
                //"Ta bort kund UPS, CUST_WAREHOUSE_11."
        ).toArray(String[]::new);

        return input[index];
    }

    private String organisationInput(int index) {
        String[] input = Arrays.asList(
                        "Vilka regioner finns?",
                        "Vilken region tillhör konsult Karin Lundqvist, CONS_100086?",
                        "Vilken region tillhör konsult Johan Björk?",
                        "Vilka konsulter är i region Linköping.",
                        "Hur många konsulter finns i region Linköping?",
                        "Hur många konsulter finns i region Stockholm?",
                        "Lista alla regioner och antal konsulter per region.",
                        "Vilken region har flest konsulter?"
                        //"Skapa en ny region som heter Nyköping med regionskod SE-NYK.",
                        //"Ta bort region Uppsala.",
                        //"Flytta konsult Karin Lundqvist, CONS_100086 till region Göteberg."
                )
                .stream()
                .toArray(String[]::new);

        return input[index];
    }

    private String otherInput(int index) {
        String[] input = Arrays.asList(
                "Vad är vädret?.",
                "Vad är klockan?."
        ).toArray(String[]::new);

        return input[index];
    }
}
