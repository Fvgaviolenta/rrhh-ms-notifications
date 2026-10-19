package com.rrhh.notifications.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Getter @Setter @Entity @Table(name="notifications_event_processed") public class ProcessedEvent {
 @Id @Column(name="event_id",length=36) private String eventId;
 @Column(name="procesado_en",nullable=false) private Instant procesadoEn;
}
