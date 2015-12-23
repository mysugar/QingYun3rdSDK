/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.emacle.qingyunsdk.internal.operation;

import static com.emacle.qingyunsdk.internal.RequestParameters.*;
import static com.emacle.qingyunsdk.common.parser.RequestMarshallers.completeMultipartUploadRequestMarshaller;
import static com.emacle.qingyunsdk.common.utils.CodingUtils.assertParameterNotNull;
import static com.emacle.qingyunsdk.common.utils.CodingUtils.assertStringNotNullOrEmpty;
import static com.emacle.qingyunsdk.common.utils.CodingUtils.checkParamRange;
import static com.emacle.qingyunsdk.common.utils.IOUtils.newRepeatableInputStream;
import static com.emacle.qingyunsdk.common.utils.LogUtils.logException;
import static com.emacle.qingyunsdk.internal.OSSUtils.OSS_RESOURCE_MANAGER;
import static com.emacle.qingyunsdk.internal.OSSUtils.addDateHeader;
import static com.emacle.qingyunsdk.internal.OSSUtils.addStringListHeader;
import static com.emacle.qingyunsdk.internal.OSSUtils.removeHeader;
import static com.emacle.qingyunsdk.internal.OSSUtils.ensureBucketNameValid;
import static com.emacle.qingyunsdk.internal.OSSUtils.ensureObjectKeyValid;
import static com.emacle.qingyunsdk.internal.OSSUtils.populateRequestMetadata;
import static com.emacle.qingyunsdk.internal.OSSUtils.trimQuotes;
import static com.emacle.qingyunsdk.internal.OSSConstants.DEFAULT_FILE_SIZE_LIMIT;
import static com.emacle.qingyunsdk.internal.OSSConstants.DEFAULT_CHARSET_NAME;
import static com.emacle.qingyunsdk.internal.ResponseParsers.completeMultipartUploadResponseParser;
import static com.emacle.qingyunsdk.internal.ResponseParsers.initiateMultipartUploadResponseParser;
import static com.emacle.qingyunsdk.internal.ResponseParsers.listMultipartUploadsResponseParser;
import static com.emacle.qingyunsdk.internal.ResponseParsers.listPartsResponseParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.emacle.qingyunsdk.exception.ClientException;
import com.emacle.qingyunsdk.exception.OSSException;
import com.emacle.qingyunsdk.common.auth.CredentialsProvider;
import com.emacle.qingyunsdk.common.comm.HttpMethod;
import com.emacle.qingyunsdk.common.comm.RequestMessage;
import com.emacle.qingyunsdk.common.comm.ResponseMessage;
import com.emacle.qingyunsdk.common.comm.ServiceClient;
import com.emacle.qingyunsdk.common.utils.HttpUtil;
import com.emacle.qingyunsdk.internal.OSSHeaders;
import com.emacle.qingyunsdk.internal.OSSOperation;
import com.emacle.qingyunsdk.internal.OSSRequestMessageBuilder;
import com.emacle.qingyunsdk.internal.OSSUtils;
import com.emacle.qingyunsdk.internal.ResponseParsers.UploadPartCopyResponseParser;
import com.emacle.qingyunsdk.model.request.AbortMultipartUploadRequest;
import com.emacle.qingyunsdk.model.request.CompleteMultipartUploadRequest;
import com.emacle.qingyunsdk.model.CannedAccessControlList;
import com.emacle.qingyunsdk.model.CompleteMultipartUploadResult;
import com.emacle.qingyunsdk.model.InitiateMultipartUploadRequest;
import com.emacle.qingyunsdk.model.request.InitiateMultipartUploadResult;
import com.emacle.qingyunsdk.model.ListMultipartUploadsRequest;
import com.emacle.qingyunsdk.model.request.ListPartsRequest;
import com.emacle.qingyunsdk.model.MultipartUploadListing;
import com.emacle.qingyunsdk.model.PartListing;
import com.emacle.qingyunsdk.model.request.UploadPartCopyRequest;
import com.emacle.qingyunsdk.model.UploadPartCopyResult;
import com.emacle.qingyunsdk.model.request.UploadPartRequest;
import com.emacle.qingyunsdk.model.UploadPartResult;

/**
 * Multipart operation.
 */
public class OSSMultipartOperation extends OSSOperation {
    
	private static final int LIST_PART_MAX_RETURNS = 1000;
	private static final int LIST_UPLOAD_MAX_RETURNS = 1000;
	private static final int MAX_PART_NUMBER = 10000;

    public OSSMultipartOperation(ServiceClient client, CredentialsProvider credsProvider) {
        super(client, credsProvider);
    }

    /**
     * Abort multipart upload.
     */
    public void abortMultipartUpload(AbortMultipartUploadRequest abortMultipartUploadRequest)
            throws OSSException, ClientException {

    	assertParameterNotNull(abortMultipartUploadRequest, "abortMultipartUploadRequest");
    	
        String key = abortMultipartUploadRequest.getKey();
        String bucketName = abortMultipartUploadRequest.getBucketName();
        String uploadId = abortMultipartUploadRequest.getUploadId();

        assertParameterNotNull(bucketName, "bucketName");
        ensureBucketNameValid(bucketName);
        assertParameterNotNull(key, "key");
        ensureObjectKeyValid(key);
        assertStringNotNullOrEmpty(uploadId, "uploadId");

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(UPLOAD_ID, uploadId);
        
        RequestMessage request = new OSSRequestMessageBuilder(getInnerClient())
                .setEndpoint(getEndpoint())
                .setMethod(HttpMethod.DELETE)
                .setBucket(bucketName)
                .setKey(key)
                .setParameters(parameters)
                .build();
        
        doOperation(request, emptyResponseParser, bucketName, key);
    }

    /**
     * Complete multipart upload. 
     */
    public CompleteMultipartUploadResult completeMultipartUpload(
            CompleteMultipartUploadRequest completeMultipartUploadRequest)
                    throws OSSException, ClientException {
    	
    	assertParameterNotNull(completeMultipartUploadRequest, "completeMultipartUploadRequest");

        String key = completeMultipartUploadRequest.getKey();
        String bucketName = completeMultipartUploadRequest.getBucketName();
        String uploadId = completeMultipartUploadRequest.getUploadId();

        assertParameterNotNull(bucketName, "bucketName");
        ensureBucketNameValid(bucketName);
        assertParameterNotNull(key, "key");
        ensureObjectKeyValid(key);
        assertStringNotNullOrEmpty(uploadId, "uploadId");

        Map<String, String> headers = new HashMap<String, String>();
        populateCompleteMultipartUploadOptionalHeaders(completeMultipartUploadRequest, headers);
        
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(UPLOAD_ID, uploadId);
        
        RequestMessage request = new OSSRequestMessageBuilder(getInnerClient())
                .setEndpoint(getEndpoint())
                .setMethod(HttpMethod.POST)
                .setBucket(bucketName)
                .setKey(key)
                .setHeaders(headers)
                .setParameters(parameters)
                .setInputStreamWithLength(completeMultipartUploadRequestMarshaller.marshall(completeMultipartUploadRequest))
                .build();
        
        return doOperation(request, completeMultipartUploadResponseParser, bucketName, key, true);
    }

    /**
     * Initiate multipart upload.
     */
    public InitiateMultipartUploadResult initiateMultipartUpload(
            InitiateMultipartUploadRequest initiateMultipartUploadRequest)
            throws OSSException, ClientException {
    	
    	assertParameterNotNull(initiateMultipartUploadRequest, "initiateMultipartUploadRequest");

        String key = initiateMultipartUploadRequest.getKey();
        String bucketName = initiateMultipartUploadRequest.getBucketName();

        assertParameterNotNull(bucketName, "bucketName");
        ensureBucketNameValid(bucketName);
        assertParameterNotNull(key, "key");
        ensureObjectKeyValid(key);

        Map<String, String> headers = new HashMap<String, String>();
        if (initiateMultipartUploadRequest.getObjectMetadata() != null) {
            populateRequestMetadata(headers,
                    initiateMultipartUploadRequest.getObjectMetadata());
        }

        // Be careful that we don't send the object's total size as the content
        // length for the InitiateMultipartUpload request.
        removeHeader(headers, OSSHeaders.CONTENT_LENGTH);

        Map<String, String> params = new HashMap<String, String>();
        params.put(SUBRESOURCE_UPLOADS, null);
        
        // Set the request content to be empty (but not null) to avoid putting parameters 
        // to request body. Set HttpRequestFactory#createHttpRequest for details.
        RequestMessage request = new OSSRequestMessageBuilder(getInnerClient())
                .setEndpoint(getEndpoint())
                .setMethod(HttpMethod.POST)
                .setBucket(bucketName)
                .setKey(key)
                .setHeaders(headers)
                .setParameters(params)
                .setInputStream(new ByteArrayInputStream(new byte[0]))
                .setInputSize(0)
                .build();
        
        return doOperation(request, initiateMultipartUploadResponseParser, bucketName, key, true);
    }

    /**
     * List multipart uploads.
     */
    public MultipartUploadListing listMultipartUploads(
            ListMultipartUploadsRequest listMultipartUploadsRequest)
            throws OSSException, ClientException {
    	
    	assertParameterNotNull(listMultipartUploadsRequest, "listMultipartUploadsRequest");

        String bucketName = listMultipartUploadsRequest.getBucketName();
        assertParameterNotNull(bucketName, "bucketName");
        ensureBucketNameValid(bucketName);

        // Use a LinkedHashMap to preserve the insertion order.
        Map<String, String> params = new LinkedHashMap<String, String>();
        populateListMultipartUploadsRequestParameters(listMultipartUploadsRequest, params);
        
        RequestMessage request = new OSSRequestMessageBuilder(getInnerClient())
                .setEndpoint(getEndpoint())
                .setMethod(HttpMethod.GET)
                .setBucket(bucketName)
                .setParameters(params)
                .build();
        
        return doOperation(request, listMultipartUploadsResponseParser, bucketName, null, true);
    }

    /**
     * List parts.
     */
    public PartListing listParts(ListPartsRequest listPartsRequest)
            throws OSSException, ClientException {
    	
    	assertParameterNotNull(listPartsRequest, "listPartsRequest");

        String key = listPartsRequest.getKey();
        String bucketName = listPartsRequest.getBucketName();
        String uploadId = listPartsRequest.getUploadId();

        assertParameterNotNull(bucketName, "bucketName");
        ensureBucketNameValid(bucketName);
        assertParameterNotNull(key, "key");
        ensureObjectKeyValid(key);
        assertStringNotNullOrEmpty(uploadId, "uploadId");
        
        // Use a LinkedHashMap to preserve the insertion order.
        Map<String, String> params = new LinkedHashMap<String, String>();
        populateListPartsRequestParameters(listPartsRequest, params);
        
        RequestMessage request = new OSSRequestMessageBuilder(getInnerClient())
                .setEndpoint(getEndpoint())
                .setMethod(HttpMethod.GET)
                .setBucket(bucketName)
                .setKey(key)
                .setParameters(params)
                .build();
        
        return doOperation(request, listPartsResponseParser, bucketName, key, true);
    }

    /**
     * Upload part.
     */
    public UploadPartResult uploadPart(UploadPartRequest uploadPartRequest)
            throws OSSException, ClientException {
    	
    	assertParameterNotNull(uploadPartRequest, "uploadPartRequest");

    	String key = uploadPartRequest.getKey();
        String bucketName = uploadPartRequest.getBucketName();
        String uploadId = uploadPartRequest.getUploadId();

        assertParameterNotNull(bucketName, "bucketName");
        ensureBucketNameValid(bucketName);
        assertParameterNotNull(key, "key");
        ensureObjectKeyValid(key);
        assertStringNotNullOrEmpty(uploadId, "uploadId");

        if (uploadPartRequest.getInputStream() == null) {
            throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getString("MustSetContentStream"));
        }
        
        InputStream repeatableInputStream = null;
        try {
			repeatableInputStream = newRepeatableInputStream(uploadPartRequest.buildPartialStream());
		} catch (IOException ex) {
			logException("Cannot wrap to repeatable input stream: ", ex);
			throw new ClientException("Cannot wrap to repeatable input stream: ", ex);
		}
        
        int partNumber = uploadPartRequest.getPartNumber();
        if (!checkParamRange(partNumber, 0, false, MAX_PART_NUMBER, true)) {
            throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getString("PartNumberOutOfRange"));
        }

        Map<String, String> headers = new HashMap<String, String>();
        populateUploadPartOptionalHeaders(uploadPartRequest, headers);

        // Use a LinkedHashMap to preserve the insertion order.
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put(PART_NUMBER, Integer.toString(partNumber));
        params.put(UPLOAD_ID, uploadId);
        
        RequestMessage request = new OSSRequestMessageBuilder(getInnerClient())
                .setEndpoint(getEndpoint())
                .setMethod(HttpMethod.PUT)
                .setBucket(bucketName)
                .setKey(key)
                .setParameters(params)
                .setHeaders(headers)
                .setInputStream(repeatableInputStream)
                .setInputSize(uploadPartRequest.getPartSize())
                .setUseChunkEncoding(uploadPartRequest.isUseChunkEncoding())
                .build();
        
        ResponseMessage response = doOperation(request, emptyResponseParser, bucketName, key);
        
        UploadPartResult result = new UploadPartResult();
        result.setPartNumber(partNumber);
        result.setETag(trimQuotes(response.getHeaders().get(OSSHeaders.ETAG)));
        return result;
    }
    
    /**
     * Upload part copy.
     */
    public UploadPartCopyResult uploadPartCopy(UploadPartCopyRequest uploadPartCopyRequest)
            throws OSSException, ClientException {
    	
    	assertParameterNotNull(uploadPartCopyRequest, "uploadPartCopyRequest");

    	String key = uploadPartCopyRequest.getKey();
        String bucketName = uploadPartCopyRequest.getBucketName();
        String uploadId = uploadPartCopyRequest.getUploadId();

        assertParameterNotNull(bucketName, "bucketName");
        ensureBucketNameValid(bucketName);
        assertParameterNotNull(key, "key");
        ensureObjectKeyValid(key);
        assertStringNotNullOrEmpty(uploadId, "uploadId");

        Long partSize = uploadPartCopyRequest.getPartSize();
        if (partSize != null) {
        	if (!checkParamRange(partSize, 0, true, DEFAULT_FILE_SIZE_LIMIT, true)) {
                throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getString("FileSizeOutOfRange"));
            }
        }
        
        int partNumber = uploadPartCopyRequest.getPartNumber();
        if (!checkParamRange(partNumber, 0, false, MAX_PART_NUMBER, true)) {
        	throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getString("PartNumberOutOfRange"));
        }

        Map<String, String> headers = new HashMap<String, String>();
        populateCopyPartRequestHeaders(uploadPartCopyRequest, headers);

        // Use a LinkedHashMap to preserve the insertion order.
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put(PART_NUMBER, Integer.toString(partNumber));
        params.put(UPLOAD_ID, uploadId);

        RequestMessage request = new OSSRequestMessageBuilder(getInnerClient())
		        .setEndpoint(getEndpoint())
		        .setMethod(HttpMethod.PUT)
		        .setBucket(bucketName)
		        .setKey(key)
		        .setParameters(params)
		        .setHeaders(headers)
		        .build();
        
        return doOperation(request, new UploadPartCopyResponseParser(partNumber), 
        		bucketName, key, true);
    }
    
    private static void populateListMultipartUploadsRequestParameters(ListMultipartUploadsRequest listMultipartUploadsRequest,
    		Map<String, String> params) {
    	
    	// Make sure 'uploads' be the first parameter.
        params.put(SUBRESOURCE_UPLOADS, null);
        
        if (listMultipartUploadsRequest.getDelimiter() != null) {
            params.put(DELIMITER, listMultipartUploadsRequest.getDelimiter());
        }
        
        if (listMultipartUploadsRequest.getKeyMarker() != null) {
            params.put(KEY_MARKER, listMultipartUploadsRequest.getKeyMarker());
        }
        
        Integer maxUploads = listMultipartUploadsRequest.getMaxUploads();
        if (maxUploads != null) {
        	if (!checkParamRange(maxUploads, 0, true, LIST_UPLOAD_MAX_RETURNS, true)) {
            	throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getFormattedString(
                		"MaxUploadsOutOfRange", LIST_UPLOAD_MAX_RETURNS));
        	}
        	params.put(MAX_UPLOADS, listMultipartUploadsRequest.getMaxUploads().toString());        	
        }

        if (listMultipartUploadsRequest.getPrefix() != null) {
            params.put(PREFIX, listMultipartUploadsRequest.getPrefix());
        }
        
        if (listMultipartUploadsRequest.getUploadIdMarker() != null) {
            params.put(UPLOAD_ID_MARKER, listMultipartUploadsRequest.getUploadIdMarker());
        }
    }
    
    private static void populateListPartsRequestParameters(ListPartsRequest listPartsRequest,
    		Map<String, String> params) {
    	
    	params.put(UPLOAD_ID, listPartsRequest.getUploadId());
        
    	Integer maxParts = listPartsRequest.getMaxParts();
        if (maxParts != null) {
        	if (!checkParamRange(maxParts, 0, true, LIST_PART_MAX_RETURNS, true)) {
        		throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getFormattedString(
        				"MaxPartsOutOfRange", LIST_PART_MAX_RETURNS));
        	}
        	params.put(MAX_PARTS, maxParts.toString());
        }
        
        Integer partNumberMarker = listPartsRequest.getPartNumberMarker();
        if (partNumberMarker != null) {
        	if (!checkParamRange(partNumberMarker, 0, false, MAX_PART_NUMBER, true)) {
            	throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getString(
            			"PartNumberMarkerOutOfRange"));
            }
        	params.put(PART_NUMBER_MARKER, partNumberMarker.toString());
        }
    }
    
    private static void populateCopyPartRequestHeaders(UploadPartCopyRequest uploadPartCopyRequest, 
    		Map<String, String> headers) {
        
    	if (uploadPartCopyRequest.getPartSize() != null) {
        	headers.put(OSSHeaders.CONTENT_LENGTH, Long.toString(uploadPartCopyRequest.getPartSize()));        	
        }
        
        if (uploadPartCopyRequest.getMd5Digest() != null) {
            headers.put(OSSHeaders.CONTENT_MD5, uploadPartCopyRequest.getMd5Digest());
        }
        
        String copySource = "/" + uploadPartCopyRequest.getSourceBucketName() + 
        		"/" + HttpUtil.urlEncode(uploadPartCopyRequest.getSourceKey(), DEFAULT_CHARSET_NAME);
        headers.put(OSSHeaders.COPY_OBJECT_SOURCE, copySource);
        
        if (uploadPartCopyRequest.getBeginIndex() != null && uploadPartCopyRequest.getPartSize() != null) {
        	String range = "bytes=" + uploadPartCopyRequest.getBeginIndex()  + "-" 
        			+ Long.toString(uploadPartCopyRequest.getBeginIndex() + uploadPartCopyRequest.getPartSize() - 1);
        	headers.put(OSSHeaders.COPY_SOURCE_RANGE, range);
        }

        addDateHeader(headers, OSSHeaders.COPY_OBJECT_SOURCE_IF_MODIFIED_SINCE,
    			uploadPartCopyRequest.getModifiedSinceConstraint());
    	addDateHeader(headers, OSSHeaders.COPY_OBJECT_SOURCE_IF_UNMODIFIED_SINCE,
    			uploadPartCopyRequest.getUnmodifiedSinceConstraint());
    	
    	addStringListHeader(headers, OSSHeaders.COPY_OBJECT_SOURCE_IF_MATCH,
    			uploadPartCopyRequest.getMatchingETagConstraints());
    	addStringListHeader(headers, OSSHeaders.COPY_OBJECT_SOURCE_IF_NONE_MATCH,
    			uploadPartCopyRequest.getNonmatchingEtagConstraints());
    }
    
    private static void populateUploadPartOptionalHeaders(UploadPartRequest uploadPartRequest, 
    		Map<String, String> headers) {
    	
    	if (!uploadPartRequest.isUseChunkEncoding()) {
    		long partSize = uploadPartRequest.getPartSize();
            if (!checkParamRange(partSize, 0, true, DEFAULT_FILE_SIZE_LIMIT, true)) {
                throw new IllegalArgumentException(OSS_RESOURCE_MANAGER.getString("FileSizeOutOfRange"));
            }
    		
    		headers.put(OSSHeaders.CONTENT_LENGTH, Long.toString(partSize));    		
    	}
    	
        if (uploadPartRequest.getMd5Digest() != null) {
            headers.put(OSSHeaders.CONTENT_MD5, uploadPartRequest.getMd5Digest());
        }
    }

    private static void populateCompleteMultipartUploadOptionalHeaders(
    		CompleteMultipartUploadRequest completeMultipartUploadRequest, Map<String, String> headers) {
    	
//    	CannedAccessControlList cannedACL = completeMultipartUploadRequest.getObjectACL();
//    	if (cannedACL != null) {
//    		if (OSSUtils.isAllowedAcl(cannedACL)) {
//    			headers.put(OSSHeaders.OSS_OBJECT_ACL, cannedACL.toString());
//    		} else {
//    			throw new IllegalArgumentException(
//    					"Unsupported acl type, please specify one of Private/PublicRead/PublicReadWrite");
//    		}    		
//    	}
    }
    
}