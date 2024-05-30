package com.example.tuningagent.services;

import com.example.tuningagent.AlarmRepository;
import com.example.tuningagent.entities.AffectException;
import com.example.tuningagent.entities.Alarm;
import com.example.tuningagent.entities.TaskToCollect;
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
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TaskToCollectService {

    @Value("${influx.bucket}")
    String bucket;

    @Autowired
    InfluxDBClient influxDBClient;
    List<TaskToCollect> tasksToCollect = new ArrayList<>();
    private final AlarmRepository alarmRepository;

    @Autowired
    public TaskToCollectService(AlarmRepository alarmRepository) {
        this.alarmRepository = alarmRepository;
    }

    @Scheduled(fixedRate = 10000)
    public void collect() throws IOException {
        System.out.println(tasksToCollect);
        if(tasksToCollect.isEmpty()){
            return;
        }
        String res = "";
        for(TaskToCollect t: tasksToCollect){

            List<Alarm> alarms = alarmRepository.selectAlarms(t.getId(),t.getSysMeas().getSysId());
            String resTask="";
            if (!t.isUseAffectsScheme()){
                alarms = t.getSysMeas().getSys().getAlarms();
                for (AffectException a: t.getAffectExceptions()){
                    if (a.getSysId().equals(t.getSysMeas().getSysId())) {
                        alarms = null;
                        break;
                    }
                }
                if(alarms==null){
                    resTask = queryInfluxByTaskToCollect(t, t.getDateStart(),t.getDateEnd());
                    System.out.println(res);
                }
                else{
                    resTask = removeAlarmData(t, alarms);
                }
            }
            else {
                resTask = removeAlarmData(t, alarms);
            }
            res += resTask;

            final Response response = Request.Post("http://localhost:8080/monitoring/data/"+t.getId().toString())
                    // .addHeader("Content-Type", "application/vnd.ms-excel")
                    .addHeader("Content-Type", "text/plain")
                    .bodyByteArray(resTask.getBytes("ISO8859-15"))
                    .execute();

        }


    }

    private String convertToInfluxDate(Timestamp timestamp){
        //yyyy-mm-dd hh:mm:ss[.fffffffff]
        String[] parts = timestamp.toString().split(" ");
        return parts[0]+'T'+parts[1].split("\\.")[0]+'Z';
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

    private String removeAlarmData(TaskToCollect t, List<Alarm> alarms){
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
                for (TaskToCollect t: tasksToCollect){
                    if (Objects.equals(t.getId(), taskToCollect.getId())){
                        tasksToCollect.remove(t);
                    }
                }
            }
        }
        return ResponseEntity.ok(HttpStatus.OK);
    }

}
