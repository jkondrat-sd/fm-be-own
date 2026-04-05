import json
import os
import re

# File paths
COLLECTION_PATH = "/Users/kbit/repos/KBIT/FM/fm-be/tests/postman/fm-be-api-tests.postman_collection.json"
OUTPUT_DIR = "/Users/kbit/repos/KBIT/FM/fm-be/tests/postman/suites"

def slugify(text):
    """
    Convert text to a URL-safe slug.
    Example: '00-Smoke' -> '00-smoke'
    """
    text = text.lower()
    text = re.sub(r'[^a-z0-9]+', '-', text)
    return text.strip('-')

def split_collection():
    if not os.path.exists(COLLECTION_PATH):
        print(f"Error: Collection not found at {COLLECTION_PATH}")
        return

    with open(COLLECTION_PATH, 'r') as f:
        full_collection = json.load(f)

    # Base collection structure
    base_info = full_collection.get('info', {})
    global_events = full_collection.get('event', [])
    suites = full_collection.get('item', [])

    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    print(f"Splitting {len(suites)} suites...")

    for suite in suites:
        suite_name = suite.get('name', 'Unknown')
        print(f"Processing suite: {suite_name}")

        # Create suite directory
        suite_slug = slugify(suite_name)
        suite_dir = os.path.join(OUTPUT_DIR, suite_slug)
        if not os.path.exists(suite_dir):
            os.makedirs(suite_dir)

        # Create new collection object
        new_collection = {
            "info": {
                "name": f"FM-BE - {suite_name}",
                "_postman_id": os.urandom(8).hex(), # New ID
                "schema": base_info.get('schema', 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json')
            },
            "item": suite.get('item', []),
            "event": global_events
        }

        # Save collection file
        file_name = f"fm-be-{suite_slug}.postman_collection.json"
        file_path = os.path.join(suite_dir, file_name)
        
        with open(file_path, 'w') as f:
            json.dump(new_collection, f, indent=2)
        
        print(f"  Saved to: {file_path}")

    print("Successfully split Postman collection into individual suites.")

if __name__ == "__main__":
    split_collection()
