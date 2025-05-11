package org.kubsu.tuning.services;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.kubsu.tuning.domain.dto.SysMeasDto;
import org.kubsu.tuning.domain.dto.TaskToCollectDto;
import org.kubsu.tuning.repositories.AlarmRepository;
import org.kubsu.tuning.domain.TaskResult;
import org.kubsu.tuning.domain.dto.TaskToCollectResultDto;
import org.kubsu.tuning.domain.entities.Alarm;
import org.kubsu.tuning.utils.PrometheusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.atomic.AtomicReference;

@Service
@KafkaListener(topics = "task-to-collect-queue-topic")
public class TaskToCollectService {
    @Value("${minio.bucket}")
    String minioBucket;

    //TODO вынести это дело в БД
    @Value("${prometheus.uri}")
    String prometheusUri;

    @Value("${prometheus.queryStep}")
    String prometheusQueryStep;

    @Value("${influx.url}")
    private String url;

    @Value("${influx.token}")
    private char[] token;

    @Value("${influx.org}")
    private String org;

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
    public void collect(TaskToCollectDto taskToCollect) throws IOException, ParseException {
        String res = "";
        String resTask = "";
        if (taskToCollect.isUseAffectScheme()) {
            List<Alarm> alarms = alarmRepository.selectAlarms(taskToCollect.getId(), taskToCollect.getSysMeas().getSysId());
            /*for (AffectException affectException : taskToCollect.getAffectExceptions()) {
                if (affectException.getSysId().equals(taskToCollect.getSysMeas().getSysId())) {
                    alarms = null;
                    break;
                }
            }*/
            if (alarms == null || alarms.isEmpty()) {
                if (taskToCollect.getSysMeas().getDataSource().equals("influx")) {
                    resTask = queryInfluxBySysMeas(taskToCollect.getSysMeas(), taskToCollect.getDateStart(), taskToCollect.getDateEnd());
                } else if (taskToCollect.getSysMeas().getDataSource().equals("prometheus")) {
                    resTask = convertPrometheusRawResponseToCsvForm(taskToCollect, queryPrometheusByTaskToCollect(taskToCollect, taskToCollect.getSysMeas()));
                }
            } else {
                if (taskToCollect.getSysMeas().getDataSource().equals("influx")) {
                    resTask = removeAlarmDataForInflux(taskToCollect, alarms);
                } else if (taskToCollect.getSysMeas().getDataSource().equals("prometheus")) {
                    resTask = removeAlarmDataForPrometheus(taskToCollect, alarms);
                }
            }
        } else {
            if (taskToCollect.getSysMeas().getDataSource().equals("influx")) {
                resTask = queryInfluxBySysMeas(taskToCollect.getSysMeas(), taskToCollect.getDateStart(), taskToCollect.getDateEnd());
            } else if (taskToCollect.getSysMeas().getDataSource().equals("prometheus")) {
                resTask = convertPrometheusRawResponseToCsvForm(taskToCollect, queryPrometheusByTaskToCollect(taskToCollect, taskToCollect.getSysMeas()));

            } else if (taskToCollect.getSysMeas().getDataSource().equals("external api")) {
                resTask = queryExternalApiByTaskToCollect(taskToCollect.getSysMeas());
            }

        }
        if (taskToCollect.getWorkloadSysMeas().getDataSource().equals("influx")) {
            res += addWorkloadData(taskToCollect, postprocessInfluxData(resTask));
        } else if (taskToCollect.getWorkloadSysMeas().getDataSource().equals("prometheus")) {
            res += addWorkloadData(taskToCollect, resTask);
        }
        System.out.println(res);

        TaskResult status = TaskResult.OK;
        String fileName = "task_to_collect_id_" + taskToCollect.getId()+".csv";
        File file = new File(".\\" + fileName);

        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(res.getBytes());
        } catch (Exception e) {
            System.out.print("File didnt written");
            status = TaskResult.ERROR;
        }
        if (status.equals(TaskResult.OK)) {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                minioClient.putObject(PutObjectArgs
                        .builder()
                        .bucket(minioBucket)
                        .object(fileName)
                        .stream(fileInputStream, -1, 10485760)
                        .build());
            } catch (Exception e) {
                System.out.print("File didnt read");
                status = TaskResult.ERROR;
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

    private String convertPrometheusRawResponseToCsvForm(TaskToCollectDto taskToCollect, String rawResponse) throws IOException {
        StringReader reader = new StringReader(rawResponse);
        ObjectMapper mapper = new ObjectMapper();
        PrometheusResponse response = mapper.readValue(reader, PrometheusResponse.class);
        List<List<String>> values = response.getData().getResult().get(0).getValues();
        response.getData().getResult().get(0).setValues(convertPrometheusValuesDateToRFC3339(values));

        String res = "";
        for (List<String> item : response.getData().getResult().get(0).getValues()) {
            res += item.get(0) + ";" + item.get(1) + ";" + taskToCollect.getSysMeas().getMeasurement().getName() + "\n";
        }
        return res;
    }

    private String queryInfluxBySysMeas(SysMeasDto sysMeasDto, Timestamp s, Timestamp e) {
        String bucket = sysMeasDto.getMeasurement().getInfluxBucketName();
        String start = convertToInfluxDate(s);
        String end = convertToInfluxDate(e);
        AtomicReference<String> flux = new AtomicReference<>("from(bucket: \"" + bucket + "\")" +
                "  |> range(start:" + start + ", stop:" + end + ")" +
                "  |> filter(fn: (r) => r[\"_measurement\"] == \"" + sysMeasDto.getMeasurement().getName() + "\")" +
                "  |> filter(fn: (r) => r[\"_field\"] == \"" + sysMeasDto.getMeasurement().getFieldName() + "\")");
        sysMeasDto.getMeasurement().getInfluxTagValues().stream()
                .forEach(tagValue -> {
                    flux.set(flux + "  |> filter(fn: (r) => r[\"" + tagValue.getTagName() + "\"] == \"" + tagValue.getValue() + "\")");
                });

        InfluxDBClient influxDBClient = InfluxDBClientFactory.create(url, token, org, bucket);
        QueryApi queryApi = influxDBClient.getQueryApi();
        String res = queryApi.queryRaw(flux.get());
        System.out.println(res);
        return res;
    }

    private String addWorkloadData(TaskToCollectDto taskToCollectDto, String data) throws IOException {
        String workloadData = "";
        if (taskToCollectDto.getWorkloadSysMeas().getDataSource().equals("influx")) {
            workloadData = queryInfluxBySysMeas(taskToCollectDto.getWorkloadSysMeas(), taskToCollectDto.getDateStart(), taskToCollectDto.getDateEnd());
            workloadData = postprocessInfluxData(workloadData);
        } else if (taskToCollectDto.getWorkloadSysMeas().getDataSource().equals("prometheus")) {
            workloadData = convertPrometheusRawResponseToCsvForm(taskToCollectDto, queryPrometheusByTaskToCollect(taskToCollectDto, taskToCollectDto.getWorkloadSysMeas()));
        }
        String[] workloadDataDivided = workloadData.split("\n");
        List<String> result = new ArrayList<>();
        for (int i = 1; i < workloadDataDivided.length - 2; i++) {
            String leftBoundary = workloadDataDivided[i];
            String rightBoundary = workloadDataDivided[i + 1];
            Timestamp periodStart = Timestamp.valueOf(leftBoundary.split(";")[0].replace("T", " ").replace("Z","").split("\\+")[0]);
            Timestamp periodEnd = Timestamp.valueOf(rightBoundary.split(";")[0].replace("T", " ").replace("Z","").split("\\+")[0]);
            for (String line: data.split("\n")) {
                if (line.startsWith("_")) {
                    continue;
                }
                Timestamp rowTimestamp = Timestamp.valueOf(line.split(";")[0].replace("T", " ").replace("Z","").split("\\+")[0]);
                if (rowTimestamp.after(periodStart) && rowTimestamp.before(periodEnd)
                        || rowTimestamp.equals(periodStart) && line.split(";").length == 3) {
                    result.add(line + ";" + leftBoundary.split(";")[1]);
                }
                if (rowTimestamp.after(periodEnd)){
                    break;
                }
            }
        }
        return String.join("\n", result);
    }

    public String queryPrometheusByTaskToCollect(TaskToCollectDto taskToCollectDto, SysMeasDto sysMeasDto) throws IOException {

        String encodedMeas = sysMeasDto.getMeasurement().getName().replaceAll("\"", "%22")
                .replaceAll("\\{", "%7B")
                .replaceAll("}", "%7D");
        System.out.println("PROMETHEUS");
        String start = convertToInfluxDate(taskToCollectDto.getDateStart());
        String end = convertToInfluxDate(taskToCollectDto.getDateEnd());

        final Response response = Request.Get(prometheusUri + "query_range?query="
                        + encodedMeas + "&start=" + start + "&end=" + end + "&step=" + prometheusQueryStep)
                // .addHeader("Content-Type", "application/vnd.ms-excel")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .execute();
        return response.returnContent().toString();
    }

    public String queryExternalApiByTaskToCollect(SysMeasDto sysMeas) throws IOException {
        System.out.println("ExternalAPI");
        final Response response = Request.Get(sysMeas.getExternalApiUrl())
                // .addHeader("Content-Type", "application/vnd.ms-excel")
                .addHeader("Content-Type", "text/plain")
                .execute();

        return response.returnContent().toString();

    }

    private String removeAlarmDataForInflux(TaskToCollectDto t, List<Alarm> alarms) {
        String res = queryInfluxBySysMeas(t.getSysMeas(), t.getDateStart(), t.getDateEnd());
        String meta = res.split("\n")[0] + '\n';
        for (Alarm a : alarms) {
            String tmp = queryInfluxBySysMeas(t.getSysMeas(), a.getDateStart(), a.getDateEnd());
            tmp = tmp.replaceAll(convertToInfluxDate(a.getDateStart()), convertToInfluxDate(t.getDateStart()));
            tmp = tmp.replaceAll(convertToInfluxDate(a.getDateEnd()), convertToInfluxDate(t.getDateEnd()));
            System.out.println(tmp);
            for (String s : tmp.split("\n")) {
                res = res.replaceFirst(s + '\n', "");
            }
        }

        return res;
    }

    private String postprocessInfluxData(String rawData) {
        String meta = rawData.split("\n")[0] + '\n';
        List<String> expectedHeaders = new ArrayList<>(List.of("_value", "_time", "_field", "_measurement"));
        Map<String, Integer> headerAndPosition = new HashMap<>();
        String[] headers =  meta.split(",");
        for (int i = 0; i < headers.length; i++) {
            if (expectedHeaders.contains(headers[i])) {
                headerAndPosition.put(headers[i], i);
            }
        }
        List<String> resRows = Arrays.asList(rawData.split("\n"));
        for (int i = 0; i < resRows.size(); i++) {
            String row = resRows.get(i);
            List<String> columns = Arrays.asList(row.split(","));
            resRows.set(i, columns.get(headerAndPosition.get("_time")) + ";"
                    + columns.get(headerAndPosition.get("_value")) + ';'
                    + columns.get(headerAndPosition.get("_measurement")) + "_" + columns.get(headerAndPosition.get("_field")));
        }
        return String.join("\n", resRows);
    }

    private String removeAlarmDataForPrometheus(TaskToCollectDto t, List<Alarm> alarms) throws IOException, ParseException {
        List<String> res = new ArrayList<>(Arrays.stream(convertPrometheusRawResponseToCsvForm(t, queryPrometheusByTaskToCollect(t, t.getSysMeas())).split("\n")).toList());
        List<String> deletedItems = new ArrayList<>();
        for (String item : res) {
            for (Alarm a : alarms) {
                Date tmpDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(item.split(";")[0]);
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
