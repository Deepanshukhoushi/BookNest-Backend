package com.booknest.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressDTO {
    private String fullName;
    private String flatHouseNo;
    private String areaStreet;
    private String landmark;
    private String city;
    private String state;
    private String pincode;
    private String mobile;
}
