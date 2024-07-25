package com.example.tuningagent.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonAutoDetect
@AllArgsConstructor
@NoArgsConstructor
@Data

public class PrometheusResponse {

    @JsonIgnore
    String status;

    PrometheusResponseData data;



}
