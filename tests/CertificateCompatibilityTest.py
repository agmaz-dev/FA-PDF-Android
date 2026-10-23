from pathlib import Path
import hashlib
import ssl
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

root = Path(__file__).resolve().parents[1]
fixtures = root / 'tests/tls-fixtures'
ca = root / 'app/src/main/res/raw/sectigo_public_root_r46.pem'
expected = '7bb647a62aeeac88bf257aa522d01ffea395e0ab45c73f93f65654ec38f25a06'
assert hashlib.sha256(ssl.PEM_cert_to_DER_cert(ca.read_text())).hexdigest() == expected

def verify(store, hostname, date):
    return subprocess.run(['openssl', 'verify', '-purpose', 'sslserver',
        '-verify_hostname', hostname, '-attime', str(int(date.timestamp())),
        '-CAfile', str(store), '-no-CApath', '-no-CAstore',
        '-untrusted', str(fixtures / 'sectigo-dv-r36.pem'),
        str(fixtures / 'apcoa-leaf.pem')], capture_output=True, text=True)

date = datetime(2026, 10, 22, 12, tzinfo=timezone.utc)
# Real observed chain cannot anchor in the legacy USERTrust root alone.
assert verify(fixtures / 'legacy-usertrust-rsa.pem', 'tms-info.apcoa.de', date).returncode != 0
# Adding the verified R46 trust anchor validates the original chain normally.
valid = verify(ca, 'tms-info.apcoa.de', date)
assert valid.returncode == 0, valid.stderr
assert verify(ca, 'other.example.org', date).returncode != 0
assert verify(ca, 'tms-info.apcoa.de', datetime(2026, 12, 4, tzinfo=timezone.utc)).returncode != 0

config = ET.parse(root / 'app/src/main/res/xml/network_security_config.xml').getroot()
assert config.find('base-config').get('cleartextTrafficPermitted') == 'false'
assert [c.get('src') for c in config.findall('./base-config/trust-anchors/certificates')] == ['system']
domains = config.findall('domain-config')
assert len(domains) == 2
domain = domains[0].find('domain')
assert domain.text == 'tms-info.apcoa.de' and domain.get('includeSubdomains') == 'false'
assert [c.get('src') for c in domains[0].findall('trust-anchors/certificates')] == ['system', '@raw/sectigo_public_root_r46']
ber_ca = root / 'app/src/main/res/raw/globalsign_root_r46.pem'
assert hashlib.sha256(ssl.PEM_cert_to_DER_cert(ber_ca.read_text())).hexdigest() == '4fa3126d8d3a11d1c4855a4f807cbad6cf919d3a5a88b03bea2c6372d93c40c9'
ber_domain = domains[1].find('domain')
assert ber_domain.text == 'ber.berlin-airport.de' and ber_domain.get('includeSubdomains') == 'false'
assert [c.get('src') for c in domains[1].findall('trust-anchors/certificates')] == ['system', '@raw/globalsign_root_r46']

def verify_ber(store, host, when):
    return subprocess.run(['openssl', 'verify', '-purpose', 'sslserver',
        '-verify_hostname', host, '-attime', str(int(when.timestamp())),
        '-CAfile', str(store), '-no-CApath', '-no-CAstore',
        '-untrusted', str(fixtures / 'globalsign-alphassl-r46.pem'),
        str(fixtures / 'ber-leaf.pem')], capture_output=True, text=True)

assert verify_ber(ca, 'ber.berlin-airport.de', date).returncode != 0
valid_ber = verify_ber(ber_ca, 'ber.berlin-airport.de', date)
assert valid_ber.returncode == 0, valid_ber.stderr
assert verify_ber(ber_ca, 'other.example.org', date).returncode != 0
assert verify_ber(ber_ca, 'ber.berlin-airport.de', datetime(2027, 3, 4, tzinfo=timezone.utc)).returncode != 0
print('PASS: echte APCOA- und BER-Ketten, falscher Host und Ablauf abgewiesen; beide CA-Ergänzungen getrennt auf die jeweilige Domain beschränkt.')
