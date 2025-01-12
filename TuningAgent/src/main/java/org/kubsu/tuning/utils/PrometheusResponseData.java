package org.kubsu.tuning.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonAutoDetect
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrometheusResponseData {

   @JsonIgnore
   String resultType;
   List<PrometheusResponseDataValue> result;

}