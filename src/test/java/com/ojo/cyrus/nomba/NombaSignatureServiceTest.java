//package com.ojo.cyrus.nomba;
//
//import com.ojo.cyrus.config.properties.NombaProperties;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import tools.jackson.databind.ObjectMapper;
//
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.Mockito.when;
//
//class NombaSignatureServiceTest {
//
//    private NombaSignatureService signatureService;
//    private NombaProperties nombaProperties;
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    @BeforeEach
//    void setUp() {
//        nombaProperties = Mockito.mock(NombaProperties.class);
//        signatureService = new NombaSignatureService(nombaProperties, objectMapper);
//    }
//
//    @Test
//    void testIsValid_WithReferenceExample() {
//        // Data from the Go example in the issue description
//        String payloadJSON = """
//          {
//          "event_type": "payment_success",
//          "requestId": "45f2dc2d-d559-4773-bba3-2d5ec17b2e20",
//          "data": {
//              "merchant": {
//              "walletId": "6756ff80aafe04a795f18b38",
//              "walletBalance": 6052,
//              "userId": "b7b10e81-e57d-41d0-8fdc-f4e23a132bbf"
//              },
//              "terminal": {},
//              "transaction": {
//              "aliasAccountNumber": "5343270516",
//              "fee": 5,
//              "sessionId": "IFAP-TRANSFER-46501-e0339485-1a2f-4b43-9bd5-fec9649e5928",
//              "type": "vact_transfer",
//              "transactionId": "API-VACT_TRA-B7B10-0435b274-807a-4bc7-8abe-9dbb4548fd7a",
//              "aliasAccountName": "ZAXBOX/EZENNA NWACHUKWU",
//              "responseCode": "",
//              "originatingFrom": "api",
//              "transactionAmount": 10,
//              "narration": "Habiblahi Hamzat Transfer 10.00 To ZAXBOX/EZENNA NWACHUKWU - Nomba",
//              "time": "2025-09-29T10:51:44Z",
//              "aliasAccountReference": "654f7c80bd4a510c90fb7f92",
//              "aliasAccountType": "VIRTUAL"
//              },
//              "customer": {
//              "bankCode": "090645",
//              "senderName": "Habiblahi Hamzat",
//              "bankName": "Nombank",
//              "accountNumber": "9617811496"
//              }
//          }
//          }""";
//
//        String expectedSignature = "Kt9095hQxfgmVbx6iz7G2tPhHdbdXgLlyY/mf35sptw=";
//        String nombaTimeStamp = "2025-09-29T10:51:44Z";
//        String secret = "HkatexKDZg7CLWy96q5sfrVHSvtoz92B";
//
//        when(nombaProperties.webhookSecret()).thenReturn(secret);
//
//        boolean isValid = signatureService.isValid(payloadJSON, expectedSignature, nombaTimeStamp);
//
//        assertTrue(isValid, "Signature should be valid based on reference example");
//    }
//}
