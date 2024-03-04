package com.vlad.crtpapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

record CrtpDocument(Description description,
                    String doc_id,
                    String doc_status,
                    String doc_type,
                    boolean importRequest,
                    String owner_inn,
                    String participant_inn,
                    String producer_inn,
                    Date production_date,
                    String production_type,
                    List<Product> products,
                    Date reg_date,
                    String reg_number
){
    public static record Description(String participantInn) {}

    public static record Product(
            String certificate_document,
            Date certificate_document_date,
            String certificate_document_number,
            String owner_inn,
            String producer_inn,
            Date production_date,
            String tnved_code,
            String uit_code,
            String uitu_code
    ) {}
}
class RateLimiter{
    private final List<Instant> lastActions = new ArrayList<>();
    private final Duration timeWindow;
    private final int actionsInTimeWindow;
    public RateLimiter(Duration timeWindow, int actionsInTimeWindow){
        this.timeWindow = timeWindow;
        this.actionsInTimeWindow = actionsInTimeWindow;
    }
    public synchronized void await() throws InterruptedException {
        if(!lastActions.isEmpty()){
            var oldestAction =lastActions.get(Math.max(lastActions.size()-actionsInTimeWindow,0));
            var windowStart = Instant.now().minus(timeWindow);
            if(oldestAction.isAfter(windowStart)){
                Thread.sleep(Duration.between(oldestAction,windowStart).toMillis());
            }
        }
        lastActions.add(Instant.now());
    }
}


public class CrtpApi {
    private static String API_URL ="https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final RateLimiter apiRequestLimiter;
    public CrtpApi(TimeUnit timeUnit,int requestLimit){
        apiRequestLimiter = new RateLimiter(Duration.of(1, timeUnit.toChronoUnit()),requestLimit);
    }
    public void createDocument(CrtpDocument document, String signature) throws InterruptedException {
            apiRequestLimiter.await();
            ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
            String json = null;
            try {
                json = objectWriter.writeValueAsString(document);
                Connection.Response execute = Jsoup.connect(API_URL)
                        .header("Content-Type", "application/json")
                        .method(Connection.Method.POST)
                        .requestBody(json)
                        .execute();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
    }
}
