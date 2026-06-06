import urllib.request
import json
import math

url = "http://localhost:8081/v1/objects"

mock_data = [
    {
        "symbol": "AAPL",
        "price": "182.50",
        "insight": "Apple Inc. (AAPL) is showing robust consumer demand and ecosystem lock-in, driving stable cash flows and margin expansion despite supply constraints. Technical indicators suggest a minor bullish divergence on the daily chart."
    },
    {
        "symbol": "MSFT",
        "price": "415.60",
        "insight": "Microsoft (MSFT) is experiencing accelerated enterprise cloud adoption, with Azure showing strong momentum powered by early generative AI monetization. Analysts maintain a bullish outlook on long-term SaaS revenues."
    },
    {
        "symbol": "IBM",
        "price": "194.20",
        "insight": "IBM's strategic transition toward hybrid cloud software and consulting services continues to yield high-margin growth. Red Hat integration remains the primary catalyst for stable, long-term enterprise contract expansion."
    },
    {
        "symbol": "AAPL",
        "price": "180.10",
        "insight": "Apple (AAPL) faces slight headwind warnings in global handset shipments, but its high-margin Services division provides a resilient bottom-line cushion. Market sentiment remains cautiously optimistic."
    },
    {
        "symbol": "MSFT",
        "price": "410.20",
        "insight": "Microsoft (MSFT) pulls back slightly after a record-breaking run, but underlying cloud workloads and copilot integrations reflect strong structural demand. Volume suggests steady institutional accumulation."
    }
]

# Generate simple 768-dimensional mock vectors based on text hash
def get_mock_vector(text):
    vector = []
    text_hash = hash(text)
    for i in range(768):
        vector.append(math.sin(text_hash + i) * 0.1)
    return vector

headers = {
    "Content-Type": "application/json"
}

for item in mock_data:
    payload = {
        "class": "MarketInsight",
        "properties": {
            "symbol": item["symbol"],
            "price": item["price"],
            "insight": item["insight"],
            "timestamp": 1780750300000.0  # mock epoch milli
        },
        "vector": get_mock_vector(item["insight"])
    }
    
    req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as response:
            res = response.read().decode('utf-8')
            print(f"Ingested {item['symbol']} successfully: {response.status}")
    except Exception as e:
        print(f"Error ingesting {item['symbol']}: {e}")
