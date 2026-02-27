package org.example.mcpserver.repository.domain;

public enum AvailabilityStatus {

    AVAILABLE,   // Konsulten är ledig och kan bokas
    BOOKED,      // Tiden är bokad
    UNAVAILABLE, // Ej tillgänglig (t.ex. manuellt spärrad)
    SICK,        // Sjuk
    VACATION     // Semester
}
