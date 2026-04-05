import json
import os

ORIGINAL_FILE = "/Users/kbit/repos/KBIT/FM/fm-be/tests/postman/fm-be-api-tests.postman_collection.json"
SUITES_DIR = "/Users/kbit/repos/KBIT/FM/fm-be/tests/postman/suites"

def count_requests(items):
    count = 0
    for item in items:
        if "request" in item:
            count += 1
        if "item" in item:
            count += count_requests(item["item"])
    return count

def verify():
    if not os.path.exists(ORIGINAL_FILE):
        print(f"Original file not found: {ORIGINAL_FILE}")
        return

    # Count original
    with open(ORIGINAL_FILE, 'r') as f:
        original_data = json.load(f)
    original_count = count_requests(original_data.get("item", []))

    # Count split suites
    split_count = 0
    suite_files = []
    for root, dirs, files in os.walk(SUITES_DIR):
        for file in files:
            if file.endswith(".postman_collection.json"):
                suite_files.append(os.path.join(root, file))

    for suite_file in suite_files:
        with open(suite_file, 'r') as f:
            suite_data = json.load(f)
        split_count += count_requests(suite_data.get("item", []))

    print(f"--- Postman Test Case Count Verification ---")
    print(f"Original Collection: {original_count} requests")
    print(f"Split Suites Total : {split_count} requests")
    
    if original_count == split_count:
        print(f"VERIFICATION PASSED: No test cases lost.")
    else:
        print(f"VERIFICATION FAILED: Difference of {original_count - split_count} requests.")

if __name__ == "__main__":
    verify()
