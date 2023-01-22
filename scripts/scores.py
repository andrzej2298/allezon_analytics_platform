import time
import requests
import datetime

with open("results.txt", "w") as output:
    while True:
        r = requests.get(
            "http://rtb101vm.rtb-lab.pl:8082/stats/dc7aa658089b0aad37774f09309b97c0"
        )
        print(
            dict(
                {stat["name"]: stat["value"] for stat in list(r.json().items())[0][1]},
                time=datetime.datetime.now().isoformat(),
            ),
            file=output,
        )
        time.sleep(3)
