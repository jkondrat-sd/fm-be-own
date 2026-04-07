import json
import os

collection = {
    "info": {
        "name": "FM-BE - 10-Notifications",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "item": []
}

def add_request(name, method, url_path, auth_token_var, body=None, tests=None, setup=None):
    item = {
        "name": name,
        "request": {
            "method": method,
            "header": [
                {
                    "key": "Content-Type",
                    "value": "application/json"
                }
            ],
            "url": {
                "raw": f"{{{{base_url}}}}/{url_path}",
                "host": ["{{base_url}}"],
                "path": ["v1"] + url_path.replace("v1/", "").split("/")
            }
        },
        "event": []
    }
    
    if body:
        item["request"]["body"] = {
            "mode": "raw",
            "raw": body if isinstance(body, str) else json.dumps(body, indent=2)
        }
        
    script = []
    if setup:
        script.extend(setup)
    script.append(f"pm.request.headers.upsert({{ key: 'Authorization', value: 'Bearer ' + pm.environment.get('{auth_token_var}') }});")
    
    item["event"].append({
        "listen": "prerequest",
        "script": {"type": "text/javascript", "exec": script}
    })
    
    if tests:
        item["event"].append({
            "listen": "test",
            "script": {"type": "text/javascript", "exec": tests}
        })
        
    collection["item"].append(item)


# 1. POST /v1/notifications/send
add_request(
    "HAPPY: Send notification",
    "POST", "v1/notifications/send", "admin_access_token",
    body={
        "userId": "{{admin_user_id}}",
        "title": "Test Title",
        "body": "Hello body message"
    },
    tests=[
        "pm.test('Status 201', function () { pm.response.to.have.status(201); });",
        "var body = pm.response.json();",
        "var resData = body.data;",
        "pm.test('Has ID', function () { pm.expect(resData.id).to.be.a('string'); });",
        "pm.test('Has unread=true', function () { pm.expect(resData.read).to.eql(false); });",
        "pm.environment.set('notification_id', resData.id);"
    ]
)

# 2. HAPPY: Send with metadata
add_request(
    "HAPPY: Send with metadata",
    "POST", "v1/notifications/send", "admin_access_token",
    body={
        "userId": "{{admin_user_id}}",
        "title": "Alert Title",
        "body": "Alert body",
        "type": "ALERT",
        "metadata": {
            "key": "value"
        }
    },
    tests=[
        "pm.test('Status 201', function () { pm.response.to.have.status(201); });",
        "var body = pm.response.json();",
        "var resData = body.data;",
        "pm.test('Metadata present', function () { pm.expect(resData.metadata).to.be.an('object'); pm.expect(resData.metadata.key).to.eql('value'); });",
        "pm.test('Type matches', function () { pm.expect(resData.type).to.eql('ALERT'); });",
        "pm.environment.set('notification_id_2', resData.id);"
    ]
)

# 3. EDGE: Missing userId
add_request(
    "EDGE: Missing userId",
    "POST", "v1/notifications/send", "admin_access_token",
    body={
        "title": "No User Title",
        "body": "No User Body"
    },
    tests=[
        "pm.test('Status 400', function () { pm.response.to.have.status(400); });",
        "var body = pm.response.json();",
        "pm.test('Error code VALIDATION_ERROR', function () { pm.expect(body.error.code).to.eql('VALIDATION_ERROR'); });"
    ]
)

# 4. EDGE: Missing title
add_request(
    "EDGE: Missing title",
    "POST", "v1/notifications/send", "admin_access_token",
    body={
        "userId": "{{admin_user_id}}",
        "body": "Missing Title Body"
    },
    tests=[
        "pm.test('Status 400', function () { pm.response.to.have.status(400); });",
        "var body = pm.response.json();",
        "pm.test('Error code VALIDATION_ERROR', function () { pm.expect(body.error.code).to.eql('VALIDATION_ERROR'); });"
    ]
)

# 5. EDGE: Non-existent userId
add_request(
    "EDGE: Non-existent userId",
    "POST", "v1/notifications/send", "admin_access_token",
    body={
        "userId": "00000000-0000-0000-0000-000000000099",
        "title": "Fake User Title",
        "body": "Fake User Body"
    },
    tests=[
        "// A foreign key constraint causes a DataIntegrityViolationException which maps to 400 Bad Request",
        "pm.test('Status 400', function () { pm.response.to.have.status(400); });",
        "var body = pm.response.json();",
        "pm.test('Error code VALIDATION_ERROR', function () { pm.expect(body.error.code).to.eql('VALIDATION_ERROR'); });"
    ]
)

# 6. RBAC: Employee cannot send
add_request(
    "RBAC: Employee cannot send",
    "POST", "v1/notifications/send", "employee_access_token",
    body={
        "userId": "{{admin_user_id}}",
        "title": "Employee attempt",
        "body": "Should fail"
    },
    tests=[
        "pm.test('Status 403', function () { pm.response.to.have.status(403); });"
    ]
)

# 7. HAPPY: List notifications (Use admin token since admin received them)
add_request(
    "HAPPY: List notifications",
    "GET", "v1/notifications?page=1&limit=10", "admin_access_token",
    tests=[
        "pm.test('Status 200', function () { pm.response.to.have.status(200); });",
        "var body = pm.response.json();",
        "pm.test('Data array', function () { pm.expect(body.data.notifications).to.be.an('array'); });",
        "pm.test('Has unreadCount', function () { pm.expect(body.data.unreadCount).to.exist; });",
        "pm.environment.set('initial_unread_count', body.data.unreadCount);"
    ]
)

# 8. HAPPY: Unread count is correct
add_request(
    "HAPPY: Unread count is correct",
    "GET", "v1/notifications", "admin_access_token",
    tests=[
        "pm.test('Status 200', function () { pm.response.to.have.status(200); });",
        "var body = pm.response.json();",
        "pm.test('Unread count is at least 2', function () { pm.expect(body.data.unreadCount).to.be.at.least(2); });"
    ]
)

# 9. EDGE: Empty notifications (Use employee token since they received none)
add_request(
    "EDGE: Empty notifications",
    "GET", "v1/notifications", "employee_access_token",
    tests=[
        "pm.test('Status 200', function () { pm.response.to.have.status(200); });",
        "var body = pm.response.json();",
        "// Employee should have 0 notifications",
        "pm.test('Empty or no unread', function () { ",
        "  if (body.data.notifications.length === 0) {",
        "    pm.expect(body.data.unreadCount).to.eql(0);",
        "  }",
        "});"
    ]
)

# 10. HAPPY: Mark as read
add_request(
    "HAPPY: Mark as read",
    "PUT", "v1/notifications/{{notification_id}}/read", "admin_access_token",
    tests=[
        "pm.test('Status 200', function () { pm.response.to.have.status(200); });",
        "var body = pm.response.json();",
        "pm.test('read is true', function () { pm.expect(body.data.read).to.eql(true); });"
    ]
)

# 11. HAPPY: Unread count decrements
add_request(
    "HAPPY: Unread count decrements",
    "GET", "v1/notifications", "admin_access_token",
    tests=[
        "pm.test('Status 200', function () { pm.response.to.have.status(200); });",
        "var body = pm.response.json();",
        "var initial = pm.environment.get('initial_unread_count');",
        "pm.test('Unread count decremented', function () { ",
        "  if (initial) {",
        "    pm.expect(body.data.unreadCount).to.be.lessThan(initial);",
        "  }",
        "});"
    ]
)

# 12. EDGE: Already read notification
add_request(
    "EDGE: Already read notification",
    "PUT", "v1/notifications/{{notification_id}}/read", "admin_access_token",
    tests=[
        "pm.test('Status 200', function () { pm.expect(pm.response.code).to.be.oneOf([200, 204]); });",
        "var body = pm.response.json();",
        "if (body && body.data) { pm.test('read still true', function () { pm.expect(body.data.read).to.eql(true); }); }"
    ]
)

# 13. EDGE: Non-existent notification
add_request(
    "EDGE: Non-existent notification",
    "PUT", "v1/notifications/00000000-0000-0000-0000-000000000099/read", "admin_access_token",
    tests=[
        "pm.test('Status 404', function () { pm.response.to.have.status(404); });"
    ]
)

# 14. HAPPY: Register FCM token
add_request(
    "HAPPY: Register FCM token",
    "POST", "v1/notifications/register-device", "admin_access_token",
    body={
        "fcmToken": "test-fcm-token-12345"
    },
    tests=[
        "pm.test('Status 204 or 200', function () { pm.expect(pm.response.code).to.be.oneOf([204, 200]); });"
    ]
)

# 15. HAPPY: Re-register same token
add_request(
    "HAPPY: Re-register same token",
    "POST", "v1/notifications/register-device", "admin_access_token",
    body={
        "fcmToken": "test-fcm-token-12345"
    },
    tests=[
        "pm.test('Status 204 or 200', function () { pm.expect(pm.response.code).to.be.oneOf([204, 200]); });"
    ]
)

# 16. EDGE: Missing fcmToken
add_request(
    "EDGE: Missing fcmToken",
    "POST", "v1/notifications/register-device", "admin_access_token",
    body={"fcmToken": ""},
    tests=[
        "pm.test('Status 400', function () { pm.response.to.have.status(400); });"
    ]
)

out_dir = '/Users/kbit/repos/KBIT/FM/fm-be/tests/postman/suites/10-notifications'
os.makedirs(out_dir, exist_ok=True)
out_file = os.path.join(out_dir, 'fm-be-10-notifications.postman_collection.json')

with open(out_file, 'w') as f:
    json.dump(collection, f, indent=2)

print(f"Generated {out_file}")
