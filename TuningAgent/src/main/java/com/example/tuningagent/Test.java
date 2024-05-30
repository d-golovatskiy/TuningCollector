package com.example.tuningagent;

import com.example.tuningagent.entities.TaskToCollect;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import org.apache.http.HttpRequest;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.hibernate.service.spi.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/data")
public class Test {


    private final InfluxDBClient influxDBClient;

    @Autowired
    public Test(InfluxDBClient influxDBClient) {
        this.influxDBClient = influxDBClient;
    }

    @GetMapping("")
    public void test() throws IOException {

        WebClient webClient = WebClient.create();
        URI url = UriComponentsBuilder.fromHttpUrl("http://localhost:8080/monitoring/data").build().toUri();
       // response.setContentType("text/csv; charset=utf-8");
        String flux = "from(bucket: \"kubsu\")" +
                "  |> range(start: -30d, stop: -3d)" +
                "  |> filter(fn: (r) => r[\"_measurement\"] == \"airSensors\")";
        QueryApi queryApi = influxDBClient.getQueryApi();
        String tables = queryApi.queryRaw(flux);
        /*File f = new File("C:\\Users\\chepu\\Desktop\\example.csv");
        try {
            // Возьмите файл
            //Создайте новый файл
            // Убедитесь, что он не существует
           if (!f.exists()){
               f.createNewFile();
           }
        }
        catch (Exception e) {
            System.err.println(e);
        }

        FileOutputStream out = new FileOutputStream(f);

        out.write(tables.getBytes("ISO8859-15"));

        out.flush();
        out.close();*/



        final Response response = Request.Post("http://localhost:8080/monitoring/data")
                .addHeader("Content-Type", "text/plain")
                .bodyByteArray(tables.getBytes("ISO8859-15"))
                .execute();



       /*WebClient.ResponseSpec  responseSpec= webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("http://localhost:8080/monitoring/data")
                        .build())
                .header("Content-Type", "application/vnd.ms-excel")
                .header("Content-Disposition", "attachment; filename=abc.csv")
                .bodyValue(BodyInserters.fromValue(tables))
                .retrieve();
                *//*.exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        return response.bodyToMono(HttpStatus.class).thenReturn(response.statusCode());
                    } else {
                        throw new ServiceException("Error uploading file");
                    }
                })*//*;
                int a =2;*/
       /* final Content postResult = Request.Post("http://localhost:8080/monitoring/data")
                .bodyString("{\"title\": \"foo\",\"body\":\"bar\",\"userId\": 1}", ContentType.)
                .execute().returnContent();*/


        //return HttpStatus.OK;
    }

    @PostMapping("/task")
    public ResponseEntity<HttpStatus> getTask(@RequestBody TaskToCollect taskToCollect){
        return ResponseEntity.ok(HttpStatus.OK);
    }

}
