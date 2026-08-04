package com.autojob.modules.cv.client;

import com.autojob.modules.cv.client.dto.CvParseRequest;
import com.autojob.modules.cv.client.dto.CvParseResponse;

public interface CvParserClient {

    CvParseResponse parse(CvParseRequest request);
}