package com.example.tuningagent;

import com.example.tuningagent.entities.Alarm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface AlarmRepository extends JpaRepository<Alarm, Long> {
    @Query(nativeQuery = true, value = "select * from alarms a where a.sys_id not in (select sys_id from affect_exception where task_id = :t_id) and a.sys_id in (select affecting_sys_id from affecting_scheme where affected_sys_id = :s_id ) or sys_id =:s_id order by date_start ASC")
    public List<Alarm> selectAlarms(@Param("t_id")Long sId,@Param("s_id") Long e);
}
