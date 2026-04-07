import json
import os

suite_name = "12. Admin Suite"
filename = "tests/postman/suites/12-admin/fm-be-12-admin.postman_collection.json"

tests = [
    {
        "name": "12.00 BOOTSTRAP: Login Super Admin",
        "method": "POST",
        "path": "v1/auth/login",
        "auth": "none",
        "body": '{\n  "email": "superadmin@fmplatform.com",\n  "password": "Admin@123456"\n}',
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.environment.set('super_admin_access_token', resData.accessToken);"
        ]
    },
    {
        "name": "12.01 HAPPY: List all audit logs",
        "method": "GET",
        "path": "v1/admin/audit-logs",
        "auth": "super_admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Is array', function() { pm.expect(resData).to.be.an('array'); });"
        ]
    },
    {
        "name": "12.02 HAPPY: Filter audit logs by userId",
        "method": "GET",
        "path": "v1/admin/audit-logs?userId={{admin_user_id}}",
        "auth": "super_admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Is array', function() { pm.expect(resData).to.be.an('array'); });"
        ]
    },
    {
        "name": "12.03 HAPPY: Filter audit logs by action",
        "method": "GET",
        "path": "v1/admin/audit-logs?action=CREATE_USER",
        "auth": "super_admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Is array', function() { pm.expect(resData).to.be.an('array'); });"
        ]
    },
    {
        "name": "12.04 EDGE: No matching audit logs",
        "method": "GET",
        "path": "v1/admin/audit-logs?userId=00000000-0000-0000-0000-000000000000",
        "auth": "super_admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Is empty array', function() { pm.expect(resData.length).to.equal(0); });"
        ]
    },
    {
        "name": "12.05 RBAC: Admin cannot view audit logs",
        "method": "GET",
        "path": "v1/admin/audit-logs",
        "auth": "admin",
        "body": "",
        "status": 403,
        "extra_test_lines": []
    },
    {
        "name": "12.06 HAPPY: Get dashboard stats",
        "method": "GET",
        "path": "v1/admin/dashboard/stats",
        "auth": "admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Has stats fields', function() { ",
            "  pm.expect(resData).to.have.property('totalUsers');",
            "  pm.expect(resData).to.have.property('totalEmployees');",
            "  pm.expect(resData).to.have.property('totalSites');",
            "});"
        ]
    },
    {
        "name": "12.07 HAPPY: Verify counts are correct (Admin auth)",
        "method": "GET",
        "path": "v1/admin/dashboard/stats",
        "auth": "admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Values are numbers', function() { ",
            "  pm.expect(resData.totalUsers).to.be.a('number');",
            "});"
        ]
    },
    {
        "name": "12.08 RBAC: Employee cannot view stats",
        "method": "GET",
        "path": "v1/admin/dashboard/stats",
        "auth": "employee",
        "body": "",
        "status": 403,
        "extra_test_lines": []
    },
    {
        "name": "12.09 HAPPY: Export users CSV",
        "method": "POST",
        "path": "v1/admin/export/users",
        "auth": "admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "pm.test('Is CSV content', function() {",
            "  pm.expect(pm.response.headers.get('Content-Disposition')).to.include('users_export.csv');",
            "});"
        ]
    },
    {
        "name": "12.10 HAPPY: Export employees CSV",
        "method": "POST",
        "path": "v1/admin/export/employees",
        "auth": "admin",
        "body": "",
        "status": 200,
        "extra_test_lines": []
    },
    {
        "name": "12.11 HAPPY: Export sites CSV",
        "method": "POST",
        "path": "v1/admin/export/sites",
        "auth": "admin",
        "body": "",
        "status": 200,
        "extra_test_lines": []
    },
    {
        "name": "12.12 EDGE: Unknown entity export",
        "method": "POST",
        "path": "v1/admin/export/foobar",
        "auth": "admin",
        "body": "",
        "status": 404,
        "extra_test_lines": []
    },
    {
        "name": "12.13 RBAC: Employee cannot export",
        "method": "POST",
        "path": "v1/admin/export/users",
        "auth": "employee",
        "body": "",
        "status": 403,
        "extra_test_lines": []
    },
    {
        "name": "12.14 HAPPY: Get system config",
        "method": "GET",
        "path": "v1/admin/config",
        "auth": "super_admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Has config property', function() { pm.expect(resData).to.have.property('config'); });"
        ]
    },
    {
        "name": "12.15 RBAC: Admin cannot view config",
        "method": "GET",
        "path": "v1/admin/config",
        "auth": "admin",
        "body": "",
        "status": 403,
        "extra_test_lines": []
    },
    {
        "name": "12.16 HAPPY: Update config value",
        "method": "PUT",
        "path": "v1/admin/config",
        "auth": "super_admin",
        "body": '{\n  "config": {\n    "testKey": "testValue"\n  }\n}',
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Config has testKey', function() { pm.expect(resData.config.testKey).to.equal('testValue'); });"
        ]
    },
    {
        "name": "12.17 HAPPY: Verify config persists",
        "method": "GET",
        "path": "v1/admin/config",
        "auth": "super_admin",
        "body": "",
        "status": 200,
        "extra_test_lines": [
            "var resData = pm.response.json().data;",
            "pm.test('Config has testKey', function() { pm.expect(resData.config.testKey).to.equal('testValue'); });"
        ]
    },
    {
        "name": "12.18 RBAC: Admin cannot update config",
        "method": "PUT",
        "path": "v1/admin/config",
        "auth": "admin",
        "body": '{\n  "config": {\n    "testKey": "adminValue"\n  }\n}',
        "status": 403,
        "extra_test_lines": []
    },
    {
        "name": "12.19 EDGE: Empty config object",
        "method": "PUT",
        "path": "v1/admin/config",
        "auth": "super_admin",
        "body": '{\n  "config": {}\n}',
        "status": 200,
        "extra_test_lines": []
    },
    {
        "name": "12.20 RBAC: Employee cannot access config",
        "method": "GET",
        "path": "v1/admin/config",
        "auth": "employee",
        "body": "",
        "status": 403,
        "extra_test_lines": []
    }
]

items = []
for t in tests:
    events = []
    pre_script = []
    
    if t["auth"] == "admin":
        pre_script.append("pm.request.headers.upsert({ key: 'Authorization', value: 'Bearer ' + pm.environment.get('admin_access_token') });")
    elif t["auth"] == "super_admin":
        pre_script.append("pm.request.headers.upsert({ key: 'Authorization', value: 'Bearer ' + pm.environment.get('super_admin_access_token') });")
    elif t["auth"] == "employee":
        pre_script.append("pm.request.headers.upsert({ key: 'Authorization', value: 'Bearer ' + pm.environment.get('employee_access_token') });")
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
        
    test_script = []
    if t["status"] in [401, 403]:
        test_script.append(f"pm.test('Status {t['status']}', function () {{ pm.expect(pm.response.code).to.be.oneOf([401, 403]); }});")
    elif t["status"] == 400:
        test_script.append(f"pm.test('Status {t['status']}', function () {{ pm.expect(pm.response.code).to.be.oneOf([400, 422]); }});")
    elif t["status"] == 404:
        test_script.append(f"pm.test('Status 404', function () {{ pm.expect(pm.response.code).to.equal(404); }});")
    else:
        test_script.append(f"pm.test('Status {t['status']}', function () {{ pm.response.to.have.status({t['status']}); }});")
        
    test_script.extend(t["extra_test_lines"])
    
    events.append({
        "listen": "test",
        "script": {
            "type": "text/javascript",
            "exec": test_script
        }
    })

    request_obj = {
        "method": t["method"],
        "header": [],
        "url": {
            "raw": "{{base_url}}/" + t["path"],
            "host": ["{{base_url}}"],
            "path": t["path"].split("?")[0].split("/")
        }
    }
    
    if "?" in t["path"]:
        query_string = t["path"].split("?")[1]
        query_params = []
        for pair in query_string.split("&"):
            k, v = pair.split("=")
            query_params.append({"key": k, "value": v})
        request_obj["url"]["query"] = query_params

    if t.get("body") and t["method"] in ["POST", "PUT", "PATCH"]:
        request_obj["header"].append({
            "key": "Content-Type",
            "value": "application/json"
        })
        request_obj["body"] = {
            "mode": "raw",
            "raw": t["body"]
        }
        
    items.append({
        "name": t["name"],
        "request": request_obj,
        "response": [],
        "event": events
    })

collection = {
    "info": {
        "name": suite_name,
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "item": items
}

os.makedirs(os.path.dirname(os.path.abspath(filename)), exist_ok=True)
with open(filename, 'w') as f:
    json.dump(collection, f, indent=2)

print(f"Postman suite generated successfully at {filename}")
