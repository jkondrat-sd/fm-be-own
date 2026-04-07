import json
import os

suite_name = "11. Media Suite"
filename = "fm-be-11-media.postman_collection.json"

tests = [
    {
        "name": "11.01 HAPPY: Presigned URL - Image",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "admin",
        "body": '{\n  "fileName": "test.jpg",\n  "contentType": "image/jpeg",\n  "fileSize": 4194304,\n  "category": "uploads"\n}',
        "status": 201,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Has uploadUrl', function() { pm.expect(resData.uploadUrl).to.not.be.empty; });",
            "pm.test('Has fileKey', function() { pm.expect(resData.fileKey).to.not.be.empty; });",
            "pm.environment.set('presigned_upload_url', resData.uploadUrl);",
            "pm.environment.set('media_file_key', resData.fileKey);"
        ]
    },
    {
        "name": "11.02 HAPPY: Presigned URL - Video",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "admin",
        "body": '{\n  "fileName": "test.mp4",\n  "contentType": "video/mp4",\n  "fileSize": 41943040,\n  "category": "uploads"\n}',
        "status": 201,
        "extra_test_lines": []
    },
    {
        "name": "11.03 HAPPY: Presigned URL - Document",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "admin",
        "body": '{\n  "fileName": "test.pdf",\n  "contentType": "application/pdf",\n  "fileSize": 8388608,\n  "category": "uploads"\n}',
        "status": 201,
        "extra_test_lines": []
    },
    {
        "name": "11.04 EDGE: File size exceeds Max Image (5MB)",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "admin",
        "body": '{\n  "fileName": "test.jpg",\n  "contentType": "image/jpeg",\n  "fileSize": 6291456,\n  "category": "uploads"\n}',
        "status": 400,
        "extra_test_lines": [
            "pm.test('Is VALIDATION_ERROR', function() { pm.expect(pm.response.json().error.code).to.eql('VALIDATION_ERROR'); });"
        ]
    },
    {
        "name": "11.05 EDGE: File size exceeds Max Video (50MB)",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "admin",
        "body": '{\n  "fileName": "test.mp4",\n  "contentType": "video/mp4",\n  "fileSize": 55000000,\n  "category": "uploads"\n}',
        "status": 400,
        "extra_test_lines": [
            "pm.test('Is VALIDATION_ERROR', function() { pm.expect(pm.response.json().error.code).to.eql('VALIDATION_ERROR'); });"
        ]
    },
    {
        "name": "11.06 EDGE: File size exceeds Max Document (10MB)",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "admin",
        "body": '{\n  "fileName": "test.pdf",\n  "contentType": "application/pdf",\n  "fileSize": 12000000,\n  "category": "uploads"\n}',
        "status": 400,
        "extra_test_lines": [
            "pm.test('Is VALIDATION_ERROR', function() { pm.expect(pm.response.json().error.code).to.eql('VALIDATION_ERROR'); });"
        ]
    },
    {
        "name": "11.07 EDGE: Unsupported Content Type",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "admin",
        "body": '{\n  "fileName": "test.zip",\n  "contentType": "application/zip",\n  "fileSize": 1024,\n  "category": "uploads"\n}',
        "status": 400,
        "extra_test_lines": [
            "pm.test('Is VALIDATION_ERROR', function() { pm.expect(pm.response.json().error.code).to.eql('VALIDATION_ERROR'); });"
        ]
    },
    {
        "name": "11.08 EDGE: Missing fields",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "admin",
        "body": '{}',
        "status": 400,
        "extra_test_lines": [
            "pm.test('Is VALIDATION_ERROR', function() { pm.expect(pm.response.json().error.code).to.eql('VALIDATION_ERROR'); });"
        ]
    },
    {
        "name": "11.09 RBAC: Presigned URL No Auth",
        "method": "POST",
        "path": "v1/media/presigned-url",
        "auth": "none",
        "body": '{\n  "fileName": "test.jpg",\n  "contentType": "image/jpeg",\n  "fileSize": 4096,\n  "category": "uploads"\n}',
        "status": 401,
        "extra_test_lines": []
    },
    {
        "name": "11.10 HAPPY: Confirm Upload - Image",
        "method": "POST",
        "path": "v1/media/confirm",
        "auth": "admin",
        "body": '{\n  "fileKey": "{{media_file_key}}"\n}',
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Has thumbnailUrl', function() { pm.expect(resData.thumbnailUrl).to.not.be.null; });"
        ]
    },
    {
        "name": "11.11 EDGE: Confirm Upload Empty File Key",
        "method": "POST",
        "path": "v1/media/confirm",
        "auth": "admin",
        "body": '{}',
        "status": 400,
        "extra_test_lines": [
            "pm.test('Is VALIDATION_ERROR', function() { pm.expect(pm.response.json().error.code).to.eql('VALIDATION_ERROR'); });"
        ]
    },
    {
        "name": "11.12 RBAC: Confirm Upload No Auth",
        "method": "POST",
        "path": "v1/media/confirm",
        "auth": "none",
        "body": '{\n  "fileKey": "{{media_file_key}}"\n}',
        "status": 401,
        "extra_test_lines": []
    }
]

items = []
for t in tests:
    # Build events
    events = []
    
    # Pre-request script for auth
    pre_script = []
    if t["auth"] == "admin":
        pre_script.append("pm.request.headers.upsert({ key: 'Authorization', value: 'Bearer ' + pm.environment.get('admin_access_token') });")
    elif t["auth"] == "none":
        pre_script.append("pm.request.headers.remove('Authorization');")
    
    if pre_script:
        events.append({
            "listen": "prerequest",
            "script": {
                "type": "text/javascript",
                "exec": pre_script
            }
        })
        
    # Test script for assertions
    if t['status'] == 401:
        test_script = [f"pm.test('Status 401 or 403', function () {{ pm.expect(pm.response.code).to.be.oneOf([401, 403]); }});"]
    else:
        test_script = [f"pm.test('Status {t['status']}', function () {{ pm.response.to.have.status({t['status']}); }});"]
    test_script.extend(t["extra_test_lines"])
    events.append({
        "listen": "test",
        "script": {
            "type": "text/javascript",
            "exec": test_script
        }
    })

    item = {
        "name": t["name"],
        "request": {
            "method": t["method"],
            "header": [
                {
                    "key": "Content-Type",
                    "value": "application/json"
                }
            ],
            "body": {
                "mode": "raw",
                "raw": t["body"]
            },
            "url": {
                "raw": "{{base_url}}/" + t["path"],
                "host": [
                    "{{base_url}}"
                ],
                "path": t["path"].split("/")
            }
        },
        "response": [],
        "event": events
    }
    items.append(item)

collection = {
    "info": {
        "name": suite_name,
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "item": items
}

with open(filename, 'w') as f:
    json.dump(collection, f, indent=2)

print(f"Postman suite generated successfully at {filename}")
