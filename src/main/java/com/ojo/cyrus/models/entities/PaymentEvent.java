//package com.ojo.cyrus.models.entities;
//
//import com.ojo.cyrus.enums.EventStatus;
//import jakarta.persistence.*;
//
//import java.util.UUID;
//
//@Entity
//@Table(
//        uniqueConstraints={
//                @UniqueConstraint(columnNames="requestId")
//        }
//)
//public class PaymentEvent {
//
//    @Id
//    @GeneratedValue
//    private UUID id;
//
//
//    private String requestId;
//
//
//    private String eventType;
//
//
//    @Lob
//    private String payload;
//
//
//    @Enumerated(EnumType.STRING)
//    private EventStatus status;
//}