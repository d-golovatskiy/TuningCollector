package org.kubsu.tuning.services;

import org.kubsu.tuning.repositories.AlarmRepository;
import org.kubsu.tuning.domain.TaskToCollectResult;
import org.kubsu.tuning.domain.dto.TaskToCollectResultDto;
import org.kubsu.tuning.domain.entities.AffectException;
import org.kubsu.tuning.domain.entities.Alarm;
import org.kubsu.tuning.domain.entities.TaskToCollect;
import org.kubsu.tuning.utils.PrometheusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@KafkaListener(topics = "task-to-collect-queue-topic")
public class TaskToCollectService {
    @Value("${influx.bucket}")
    String bucket;

    @Value("${minio.bucket}")
    String minioBucket;

    @Value("${prometheus.uri}")
    String prometheusUri;

    @Value("${prometheus.queryStep}")
    String prometheusQueryStep;

    @Autowired
    InfluxDBClient influxDBClient;

    @Autowired
    MinioClient minioClient;

    private final KafkaTemplate<String, TaskToCollectResultDto> kafkaTemplate;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final AlarmRepository alarmRepository;

    @Autowired
    public TaskToCollectService(AlarmRepository alarmRepository, KafkaTemplate<String, TaskToCollectResultDto> kafkaTemplate) {
        this.alarmRepository = alarmRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaHandler
    public void collect(TaskToCollect taskToCollect) throws IOException, ParseException {
        String res = "";
        List<Alarm> alarms = alarmRepository.selectAlarms(taskToCollect.getId(), taskToCollect.getSysMeas().getSysId());
        String resTask = "";
        if (taskToCollect.isUseAffectsScheme()) {
            alarms = taskToCollect.getSysMeas().getSys().getAlarms();
            for (AffectException a : taskToCollect.getAffectExceptions()) {
                if (a.getSysId().equals(taskToCollect.getSysMeas().getSysId())) {
                    alarms = null;
                    break;
                }
            }
            if (alarms == null) {
                if (taskToCollect.getDataSource().equals("influx")) {
                    resTask = queryInfluxByTaskToCollect(taskToCollect, taskToCollect.getDateStart(), taskToCollect.getDateEnd());
                } else if (taskToCollect.getDataSource().equals("prometheus")) {
                    resTask = convertPrometheusRawResponseToCsvForm(taskToCollect, queryPrometheusByTaskToCollect(taskToCollect));
                }
            } else {
                if (taskToCollect.getDataSource().equals("influx")) {
                    resTask = removeAlarmDataForInflux(taskToCollect, alarms);
                } else if (taskToCollect.getDataSource().equals("prometheus")) {
                    resTask = removeAlarmDataForPrometheus(taskToCollect, alarms);
                }
            }
        } else {
            if (taskToCollect.getDataSource().equals("influx")) {
                resTask = queryInfluxByTaskToCollect(taskToCollect, taskToCollect.getDateStart(), taskToCollect.getDateEnd());
            } else if (taskToCollect.getDataSource().equals("prometheus")) {
                resTask = convertPrometheusRawResponseToCsvForm(taskToCollect, queryPrometheusByTaskToCollect(taskToCollect));

            } else if (taskToCollect.getDataSource().equals("external api")) {
                resTask = queryExternalApiByTaskToCollect(taskToCollect);
            }

        }
        res += resTask;
        System.out.println(res);

        TaskToCollectResult status = TaskToCollectResult.OK;
        String fileName = "task_to_collect_id_" + taskToCollect.getId()+".csv";
        File file = new File(".\\" + fileName);

        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(res.getBytes());
        } catch (Exception e) {
            System.out.print("File didnt written");
            status = TaskToCollectResult.ERROR;
        }
        if (status.equals(TaskToCollectResult.OK)) {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                minioClient.putObject(PutObjectArgs
                        .builder()
                        .bucket(minioBucket)
                        .object(fileName)
                        .stream(fileInputStream, -1, 10485760)
                        .build());
            } catch (Exception e) {
                System.out.print("File didnt read");
                status = TaskToCollectResult.ERROR;
            }
        }
        if (file.exists()) {
            file.delete();
        }
        TaskToCollectResultDto taskToCollectResultDto = new TaskToCollectResultDto(taskToCollect.getId(), status);

        CompletableFuture<SendResult<String, TaskToCollectResultDto>> future = kafkaTemplate.send("task-to-collect-result-topic",
                taskToCollectResultDto.getTaskId().toString(),
                taskToCollectResultDto);
        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Failed to send message: {}", exception.getMessage());
            } else {
                log.info("Message sent successful: {}", result.getRecordMetadata());
            }
        });
        future.join();
        log.info("Return: {}", taskToCollect.getId());
    }

    //TODO вынести такие вспомогательные методы в отдельные util-классы в разрезе провайдеров
    private String convertToInfluxDate(Timestamp timestamp) {
        //yyyy-mm-dd hh:mm:ss[.fffffffff]
        String[] parts = timestamp.toString().split(" ");
        return parts[0] + 'T' + parts[1].split("\\.")[0] + 'Z';
    }

    private List<List<String>> convertPrometheusValuesDateToRFC3339(List<List<String>> values) {

        for (List<String> item : values) {
            item.set(0, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date(Long.parseLong(item.get(0)) * 1000L)));
            System.out.println(item);
        }
        return values;
    }

    private String convertPrometheusRawResponseToCsvForm(TaskToCollect t, String rawResponse) throws IOException {
        StringReader reader = new StringReader(rawResponse);
        ObjectMapper mapper = new ObjectMapper();
        PrometheusResponse response = mapper.readValue(reader, PrometheusResponse.class);
        List<List<String>> values = response.getData().getResult().get(0).getValues();
        response.getData().getResult().get(0).setValues(convertPrometheusValuesDateToRFC3339(values));

        String res = "";
        for (List<String> item : response.getData().getResult().get(0).getValues()) {
            res += item.get(0) + "," + item.get(1) + "," + t.getSysMeas().getMeasurements().getName() + "\n";
        }
        return res;
    }

    private String queryInfluxByTaskToCollect(TaskToCollect t, Timestamp s, Timestamp e) {
        String start = convertToInfluxDate(s);
        String end = convertToInfluxDate(e);
        String flux = "from(bucket: \"" + bucket + "\")" +
                "  |> range(start:" + start + ", stop:" + end + ")" +
                "  |> filter(fn: (r) => r[\"_measurement\"] == \"" + t.getSysMeas().getMeasurements().getName() + "\")" +
                "  |> filter(fn: (r) => r[\"_field\"] == \"" + t.getSysMeas().getMeasurements().getFieldName() + "\")";
        QueryApi queryApi = influxDBClient.getQueryApi();
        String res = queryApi.queryRaw(flux);
        System.out.println(res);
        return res;
    }

    public String queryPrometheusByTaskToCollect(TaskToCollect t) throws IOException {

        String encodedMeas = t.getSysMeas().getMeasurements().getName().replaceAll("\"", "%22")
                .replaceAll("\\{", "%7B")
                .replaceAll("}", "%7D");
        System.out.println("PROMETHEUS");
        String start = convertToInfluxDate(t.getDateStart());
        String end = convertToInfluxDate(t.getDateEnd());

        final Response response = Request.Get(prometheusUri + "query_range?query="
                        + encodedMeas + "&start=" + start + "&end=" + end + "&step=" + prometheusQueryStep)
                // .addHeader("Content-Type", "application/vnd.ms-excel")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .execute();
        return response.returnContent().toString();
    }

    public String queryExternalApiByTaskToCollect(TaskToCollect t) throws IOException {
        System.out.println("ExternalAPI");
        final Response response = Request.Get(t.getSysMeas().getExternalApiUri())
                // .addHeader("Content-Type", "application/vnd.ms-excel")
                .addHeader("Content-Type", "text/plain")
                .execute();

        return response.returnContent().toString();

    }

    //может получше как-то можно???
    private String removeAlarmDataForInflux(TaskToCollect t, List<Alarm> alarms) {
        String res = queryInfluxByTaskToCollect(t, t.getDateStart(), t.getDateEnd());
        String meta = res.split("\n")[0] + '\n';
        for (Alarm a : alarms) {
            String tmp = queryInfluxByTaskToCollect(t, a.getDateStart(), a.getDateEnd());
            tmp = tmp.replaceAll(convertToInfluxDate(a.getDateStart()), convertToInfluxDate(t.getDateStart()));
            tmp = tmp.replaceAll(convertToInfluxDate(a.getDateEnd()), convertToInfluxDate(t.getDateEnd()));
            System.out.println(tmp);
            for (String s : tmp.split("\n")) {
                res = res.replaceFirst(s + '\n', "");
            }
        }

        //postprocess
        List<String> expectedHeaders = new ArrayList<>(List.of("_value", "_time", "_field", "_measurement"));
        Map<String, Integer> headerAndPosition = new HashMap<>();
        String[] headers =  meta.split(",");
        for (int i = 0; i < headers.length; i++) {
            if (expectedHeaders.contains(headers[i])) {
                headerAndPosition.put(headers[i], i);
            }
        }
        List<String> resRows = Arrays.asList(res.split("\n"));
        for (int i = 0; i < resRows.size(); i++) {
            String row = resRows.get(i);
            List<String> columns = Arrays.asList(row.split(","));
            resRows.set(i, columns.get(headerAndPosition.get("_time")) + ","
                    + columns.get(headerAndPosition.get("_value")) + ','
                    + columns.get(headerAndPosition.get("_measurement")) + "_" + columns.get(headerAndPosition.get("_field")));
        }

        res = String.join("\n", resRows);
        System.out.println(res);
        return res;
    }

    private String removeAlarmDataForPrometheus(TaskToCollect t, List<Alarm> alarms) throws IOException, ParseException {
        List<String> res = new ArrayList<>(Arrays.stream(convertPrometheusRawResponseToCsvForm(t, queryPrometheusByTaskToCollect(t)).split("\n")).toList());
        List<String> deletedItems = new ArrayList<>();
        for (String item : res) {
            for (Alarm a : alarms) {
                Date tmpDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(item.split(",")[0]);
                if (tmpDate.after(a.getDateStart()) && tmpDate.before(a.getDateEnd())) {
                    res.set(res.indexOf(item), "");
                    deletedItems.add(item);
                    break;
                }
            }
        }
        StringBuilder ret = new StringBuilder();
        for (String item : res) {
            if (!item.isEmpty()) {
                ret.append(item).append('\n');
            }
        }

        System.out.println(ret);
        return ret.toString();
    }
}
