package com.example.tuningagent.services;

import com.example.tuningagent.AlarmRepository;
import com.example.tuningagent.entities.AffectException;
import com.example.tuningagent.entities.Alarm;
import com.example.tuningagent.entities.TaskToCollect;
import com.example.tuningagent.utils.PrometheusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class TaskToCollectService {

    @Value("${influx.bucket}")
    String bucket;

    @Value("${prometheus.uri}")
    String prometheusUri;

    @Value("${prometheus.queryStep}")
    String prometheusQueryStep;

    @Autowired
    InfluxDBClient influxDBClient;
    List<TaskToCollect> tasksToCollect = new ArrayList<>();
    private final AlarmRepository alarmRepository;


    @Autowired
    public TaskToCollectService(AlarmRepository alarmRepository, @Value("${tuning.scheduler.timeout}") long delay) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        this.alarmRepository = alarmRepository;

        Method method = this.getClass().getDeclaredMethod("collect");
        method.setAccessible(true);
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        InvocationHandler invocationHandler = Proxy.getInvocationHandler(scheduled);
        Field memberValues = invocationHandler.getClass().getDeclaredField("memberValues");
        memberValues.setAccessible(true);
        Map<String, Object> values = (Map<String, Object>) memberValues.get(invocationHandler);
        values.put("fixedRate", delay);
    }



    @Scheduled()
    public void collect() throws IOException, ParseException {
        System.out.println(tasksToCollect);
        if(tasksToCollect.isEmpty()){
            return;
        }
        String res = "";
        for(TaskToCollect t: tasksToCollect){

            // с логикой использования алармов бред какой-то
            List<Alarm> alarms = alarmRepository.selectAlarms(t.getId(),t.getSysMeas().getSysId());
            String resTask="";
            if (t.isUseAffectsScheme()){
                alarms = t.getSysMeas().getSys().getAlarms();
                for (AffectException a: t.getAffectExceptions()){
                    if (a.getSysId().equals(t.getSysMeas().getSysId())) {
                        alarms = null;
                        break;
                    }
                }
                if(alarms==null){
                    if(t.getDataSource().equals("influx")) {
                        resTask = queryInfluxByTaskToCollect(t, t.getDateStart(), t.getDateEnd());
                    }
                    else if (t.getDataSource().equals("prometheus")) {
                        resTask = convertPrometheusRawResponseToCsvForm(t,queryPrometheusByTaskToCollect(t));
                    }
                }
                else{
                    if(t.getDataSource().equals("influx")) {
                        resTask = removeAlarmDataForInflux(t, alarms);
                    }
                    else if (t.getDataSource().equals("prometheus")) {
                        resTask = removeAlarmDataForPrometheus(t, alarms);
                    }

                }


            }
            else {
                if(t.getDataSource().equals("influx")) {
                    resTask = queryInfluxByTaskToCollect(t, t.getDateStart(), t.getDateEnd());
                }
                else if (t.getDataSource().equals("prometheus")) {
                    resTask = convertPrometheusRawResponseToCsvForm(t,queryPrometheusByTaskToCollect(t));

                }
                else if(t.getDataSource().equals("external api")){
                    resTask = queryExternalApiByTaskToCollect(t);
                }

            }

            res += resTask;
            System.out.println(res);

            final Response response = Request.Post("http://localhost:8080/monitoring/data/"+t.getId().toString())
                    // .addHeader("Content-Type", "application/vnd.ms-excel")
                    .addHeader("Content-Type", "text/plain")
                    .bodyByteArray(resTask.getBytes("ISO8859-15"))
                    .execute();
        }
    }

    //TODO вынести такие вспомогательные методы в отдельные util-классы в разрезе провайдеров
    private String convertToInfluxDate(Timestamp timestamp){
        //yyyy-mm-dd hh:mm:ss[.fffffffff]
        String[] parts = timestamp.toString().split(" ");
        return parts[0]+'T'+parts[1].split("\\.")[0]+'Z';
    }

    private List<List<String>> convertPrometheusValuesDateToRFC3339(List<List<String>> values){

        for(List<String> item: values){
            item.set(0,new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(new Date(Long.parseLong(item.get(0))*1000L)));
            System.out.println(item);
        }
        return values;
    }

    private String convertPrometheusRawResponseToCsvForm( TaskToCollect t, String rawResponse) throws IOException {
        StringReader reader = new StringReader(rawResponse);
        ObjectMapper mapper = new ObjectMapper();
        PrometheusResponse response = mapper.readValue(reader, PrometheusResponse.class);
        List<List<String>> values = response.getData().getResult().get(0).getValues();
        response.getData().getResult().get(0).setValues(convertPrometheusValuesDateToRFC3339(values));

        String res = "";
        for(List<String> item: response.getData().getResult().get(0).getValues()){
            res+=item.get(0)+","+item.get(1)+","+t.getSysMeas().getMeasurements().getName()+"\n";
        }
        return res;
    }

    private String queryInfluxByTaskToCollect(TaskToCollect t, Timestamp s, Timestamp e){
        String start = convertToInfluxDate(s);
        String end = convertToInfluxDate(e);
        String flux = "from(bucket: \""+bucket+"\")" +
                "  |> range(start:"+ start+", stop:"+end +")" +
                "  |> filter(fn: (r) => r[\"_measurement\"] == \""+t.getSysMeas().getMeasurements().getName()+"\")";
        QueryApi queryApi = influxDBClient.getQueryApi();
        String res = queryApi.queryRaw(flux);
        System.out.println(res);
        return res;
    }

    public String queryPrometheusByTaskToCollect(TaskToCollect t) throws IOException {

        String encodedMeas = t.getSysMeas().getMeasurements().getName().replaceAll("\"","%22")
                .replaceAll("\\{","%7B")
                .replaceAll("}","%7D");
        System.out.println("PROMETHEUS");
        String start = convertToInfluxDate(t.getDateStart());
        String end = convertToInfluxDate(t.getDateEnd());

        final Response response = Request.Get(prometheusUri+"query_range?query="
                        + encodedMeas +"&start="+start+"&end="+end+"&step="+prometheusQueryStep)
                // .addHeader("Content-Type", "application/vnd.ms-excel")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .execute();

        return response.returnContent().toString();
    }

    public String queryExternalApiByTaskToCollect(TaskToCollect t) throws IOException{
        System.out.println("ExternalAPI");
        final Response response = Request.Get(t.getSysMeas().getExternalApiUri())
                // .addHeader("Content-Type", "application/vnd.ms-excel")
                .addHeader("Content-Type", "text/plain")
                .execute();

        return response.returnContent().toString();

    }

    //может получше как-то можно???
    private String removeAlarmDataForInflux(TaskToCollect t, List<Alarm> alarms){
        String res = queryInfluxByTaskToCollect(t,t.getDateStart(),t.getDateEnd());
        String meta = res.split("\n")[0]+'\n';
        for (Alarm a: alarms){
            String tmp = queryInfluxByTaskToCollect(t,a.getDateStart(),a.getDateEnd());
            tmp = tmp.replaceAll(convertToInfluxDate(a.getDateStart()), convertToInfluxDate(t.getDateStart()));
            tmp = tmp.replaceAll(convertToInfluxDate(a.getDateEnd()), convertToInfluxDate(t.getDateEnd()));
            System.out.println(tmp);

            for(String s: tmp.split("\n")){
                res = res.replaceFirst(s+'\n',"");
            }

        }
        res = meta + res;
        System.out.println(res);
        return res;
    }

    private String removeAlarmDataForPrometheus(TaskToCollect t, List<Alarm> alarms) throws IOException, ParseException {
        List<String> res = new ArrayList<>(Arrays.stream(convertPrometheusRawResponseToCsvForm(t, queryPrometheusByTaskToCollect(t)).split("\n")).toList());
        List<String> deletedItems = new ArrayList<>();
        for(String item: res){
            for (Alarm a: alarms){
                    Date tmpDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(item.split(",")[0]);
                    if(tmpDate.after(a.getDateStart()) && tmpDate.before(a.getDateEnd())){
                        res.set(res.indexOf(item),"");
                        deletedItems.add(item);
                        break;
                    }
            }
        }
        StringBuilder ret = new StringBuilder();
        for(String item: res){
            if(!item.isEmpty()) {
                ret.append(item).append('\n');
            }
        }

        System.out.println(ret);
        return ret.toString();
    }




    public ResponseEntity<HttpStatus> actionTaskToCollect(TaskToCollect taskToCollect){
        if (taskToCollect.getStatus().equals("started")){
            tasksToCollect.add(taskToCollect);
        }
        else{
            if(taskToCollect.getStatus().equals("stopped")){
                for (TaskToCollect t: tasksToCollect){
                    if (Objects.equals(t.getId(), taskToCollect.getId())){
                        t.setStatus("stopped");
                    }
                }
            }
            if(taskToCollect.getStatus().equals("finished")){
                tasksToCollect.removeIf(t -> Objects.equals(t.getId(), taskToCollect.getId()));
            }
        }
        return ResponseEntity.ok(HttpStatus.OK);
    }

}
