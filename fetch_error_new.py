import urllib.request
import urllib.error

url = 'https://cogniflowservice-349799058791.europe-west1.run.app/api/insights/search?query=growth%20trends&limit=6'
req = urllib.request.Request(url)
try:
    response = urllib.request.urlopen(req)
    print("Success:", response.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print("Status code:", e.code)
    print("Error response:")
    print(e.read().decode('utf-8'))
except Exception as ex:
    print("Other error:", ex)
