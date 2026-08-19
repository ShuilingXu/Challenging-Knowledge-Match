#!/usr/bin/env python3
import base64
import datetime
import hashlib
import hmac
import json
import sqlite3
import sys
import urllib.parse
import urllib.request
import uuid


API_URL = "https://alidns.aliyuncs.com/"
API_VERSION = "2015-01-09"


def percent_encode(value):
    return urllib.parse.quote(str(value), safe="~")


def request(access_key, secret_key, action, **arguments):
    parameters = {
        "AccessKeyId": access_key,
        "Action": action,
        "Format": "JSON",
        "SignatureMethod": "HMAC-SHA1",
        "SignatureNonce": str(uuid.uuid4()),
        "SignatureVersion": "1.0",
        "Timestamp": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "Version": API_VERSION,
        **arguments,
    }
    canonical = "&".join(
        f"{percent_encode(key)}={percent_encode(parameters[key])}" for key in sorted(parameters)
    )
    string_to_sign = f"GET&%2F&{percent_encode(canonical)}"
    signature = base64.b64encode(
        hmac.new(f"{secret_key}&".encode(), string_to_sign.encode(), hashlib.sha1).digest()
    ).decode()
    parameters["Signature"] = signature
    url = API_URL + "?" + urllib.parse.urlencode(parameters)
    with urllib.request.urlopen(url, timeout=20) as response:
        return json.load(response)


def credentials(database):
    with sqlite3.connect(database) as connection:
        row = connection.execute(
            "select authorization from website_dns_accounts where type = 'AliYun' order by id limit 1"
        ).fetchone()
    if row is None:
        raise RuntimeError("No AliYun DNS account exists in 1Panel")
    authorization = json.loads(row[0])
    return authorization["accessKey"], authorization["secretKey"]


def upsert(access_key, secret_key, domain, rr, address):
    result = request(
        access_key,
        secret_key,
        "DescribeDomainRecords",
        DomainName=domain,
        RRKeyWord=rr,
        SearchMode="EXACT",
        TypeKeyWord="A",
        PageSize="100",
    )
    records = [
        record
        for record in result.get("DomainRecords", {}).get("Record", [])
        if record.get("RR") == rr and record.get("Type") == "A"
    ]
    if not records:
        request(
            access_key,
            secret_key,
            "AddDomainRecord",
            DomainName=domain,
            RR=rr,
            Type="A",
            Value=address,
        )
        print(f"created {rr}.{domain} -> {address}")
        return
    if len(records) > 1:
        raise RuntimeError(f"Multiple A records already exist for {rr}.{domain}")
    record = records[0]
    if record.get("Value") == address:
        print(f"unchanged {rr}.{domain} -> {address}")
        return
    request(
        access_key,
        secret_key,
        "UpdateDomainRecord",
        RecordId=record["RecordId"],
        RR=rr,
        Type="A",
        Value=address,
    )
    print(f"updated {rr}.{domain} -> {address}")


def main():
    if len(sys.argv) < 5:
        raise SystemExit("usage: alidns_records.py DATABASE DOMAIN ADDRESS RR [RR ...]")
    database, domain, address, *records = sys.argv[1:]
    access_key, secret_key = credentials(database)
    for record in records:
        upsert(access_key, secret_key, domain, record, address)


if __name__ == "__main__":
    main()
