"""Kiem tra HTTPS that cua Backend (Render) va AI Service (Vercel): chung chi hop le va khop ten mien, TLS >= 1.2,
chung chi con han, HTTP chuyen huong sang HTTPS. In them ma ghim SPKI (sha256/...) cho OkHttp CertificatePinner
(ke hoach muc 8.2): chuoi chung chi dang duoc phuc vu va cac root CA cua Let's Encrypt / Google Trust Services.
Khong can bi mat; URL doc tu bien moi truong BASE_URL, AI_SERVICE_BASE_URL. Thoat ma 1 neu co FAIL.
Chay: docker run --rm -e BASE_URL -e AI_SERVICE_BASE_URL -v "${PWD}:/repo" -w /repo/tools/security-audit python:3.13-slim sh -c "pip install -q cryptography certifi && python -u https_audit.py"
"""
import base64
import datetime
import hashlib
import http.client
import os
import socket
import ssl
import sys
import warnings
from urllib.parse import urlparse

import certifi
from cryptography import x509
from cryptography.hazmat.primitives import serialization

TIMEOUT_SECONDS = 60
MIN_DAYS_LEFT = 14
ROOT_NAME_PREFIXES = ("ISRG Root", "GTS Root")
REDIRECT_STATUSES = (301, 302, 307, 308)
TARGETS = (("Backend (Render)", "BASE_URL"), ("AI Service (Vercel)", "AI_SERVICE_BASE_URL"))

results = []


def record(name, ok, detail):
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  {name}  ({detail})")


def spki_pin(cert):
    der = cert.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    return "sha256/" + base64.b64encode(hashlib.sha256(der).digest()).decode()


def common_name(name):
    attributes = name.get_attributes_for_oid(x509.NameOID.COMMON_NAME)
    return attributes[0].value if attributes else name.rfc4514_string()


def fetch_chain(host, port):
    context = ssl.create_default_context(cafile=certifi.where())
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    with socket.create_connection((host, port), timeout=TIMEOUT_SECONDS) as sock:
        with context.wrap_socket(sock, server_hostname=host) as tls:
            chain = [x509.load_der_x509_certificate(der) for der in tls.get_verified_chain()]
            return tls.version(), chain


def root_pins():
    with open(certifi.where(), "rb") as bundle, warnings.catch_warnings():
        # mot root cu trong bo certifi co so serial am, cryptography chi canh bao, khong anh huong ma ghim
        warnings.filterwarnings("ignore", message="Parsed a serial number")
        roots = x509.load_pem_x509_certificates(bundle.read())
    return {spki_pin(c): common_name(c.subject) for c in roots if common_name(c.subject).startswith(ROOT_NAME_PREFIXES)}


def check_redirect(host):
    connection = http.client.HTTPConnection(host, 80, timeout=TIMEOUT_SECONDS)
    try:
        connection.request("GET", "/")
        response = connection.getresponse()
        location = response.getheader("Location") or ""
        record("HTTP chuyen sang HTTPS", response.status in REDIRECT_STATUSES and location.startswith("https://"),
               f"HTTP {response.status}, Location={location[:60]}")
    except OSError as error:
        record("HTTP chuyen sang HTTPS", False, repr(error))
    finally:
        connection.close()


def audit(label, base_url, known_roots):
    url = urlparse(base_url)
    host, port = url.hostname, url.port or 443
    print(f"\n== {label}: {host} ==")
    try:
        version, chain = fetch_chain(host, port)
    except (ssl.SSLError, OSError) as error:
        record("Chung chi hop le, khop ten mien", False, repr(error))
        return
    leaf = chain[0]
    days_left = (leaf.not_valid_after_utc - datetime.datetime.now(datetime.timezone.utc)).days
    record("Chung chi hop le, khop ten mien", True, f"{len(chain)} chung chi trong chuoi")
    record("TLS >= 1.2", True, version)
    record(f"Chung chi con han >= {MIN_DAYS_LEFT} ngay", days_left >= MIN_DAYS_LEFT,
           f"con {days_left} ngay, het han {leaf.not_valid_after_utc:%Y-%m-%d}, cap {leaf.not_valid_before_utc:%Y-%m-%d}")
    check_redirect(host)
    print("Chuoi chung chi (la -> root):")
    for index, cert in enumerate(chain):
        pin = spki_pin(cert)
        note = f"  <- khop {known_roots[pin]}" if pin in known_roots else ""
        print(f"  [{index}] {common_name(cert.subject)} | cap boi {common_name(cert.issuer)} | {pin}{note}")


def main():
    known_roots = root_pins()
    for label, variable in TARGETS:
        base_url = os.environ.get(variable, "")
        if not base_url:
            sys.exit(f"Thieu bien moi truong {variable}")
        audit(label, base_url, known_roots)
    print("\nRoot CA co the Render dung (Let's Encrypt, Google Trust Services):")
    for pin, name in sorted(known_roots.items(), key=lambda item: item[1]):
        print(f"  {name} | {pin}")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
