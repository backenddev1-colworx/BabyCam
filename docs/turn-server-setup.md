# BabyCam — free lifetime TURN server (Oracle Cloud Always Free + coturn)

BabyCam needs a TURN relay so video reaches the parent on **any** network (different networks,
strict NAT, client-isolated Wi-Fi). Public free TURN servers are unreliable or capped, so we
self-host **coturn** on Oracle Cloud's **Always Free** tier — lifetime free, ~10 TB/month egress,
zero ongoing cost.

You do the Oracle steps (account + VM); the coturn config is in `docs/turnserver.conf`. Once the
server is up, send the maintainer the **public IP + the password you set** and they'll drop it into
`WebRtcSession.iceServers()`.

---

## 1. Create the Always Free VM

1. Sign up at https://www.oracle.com/cloud/free/ (needs a card for identity check — **never charged**
   on Always Free resources; pick "Always Free" shapes only).
2. Console → **Compute → Instances → Create instance**:
   - **Image:** Canonical Ubuntu 22.04
   - **Shape:** `VM.Standard.E2.1.Micro` (AMD, Always Free) — 1 GB RAM is plenty for coturn.
   - **Add SSH keys:** download/save the private key (you'll SSH with it).
   - Leave networking default (it creates a VCN + public subnet). **Assign a public IPv4** (default).
3. After it boots, note the **Public IP address** (instance details page).

## 2. Open the ports in Oracle's cloud firewall (Security List)

This is the #1 gotcha — Oracle blocks everything by default at the cloud level.

Console → **Networking → Virtual Cloud Networks → (your VCN) → Security Lists → Default Security
List → Add Ingress Rules**. Add these (Source CIDR `0.0.0.0/0`):

| Protocol | Destination port range | Purpose          |
|----------|------------------------|------------------|
| TCP      | 3478                   | TURN/STUN        |
| UDP      | 3478                   | TURN/STUN        |
| TCP      | 5349                   | TURN over TLS    |
| UDP      | 5349                   | TURN over TLS    |
| UDP      | 49152-65535            | media relay range|

(Port 22 TCP for SSH is already open by default.)

## 3. Install + configure coturn on the VM

SSH in: `ssh -i /path/to/private.key ubuntu@PUBLIC_IP`

```bash
sudo apt update && sudo apt install -y coturn

# find your IPs
PUBLIC_IP=$(curl -s ifconfig.me); echo "public=$PUBLIC_IP"
PRIVATE_IP=$(ip -4 addr show | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | grep -v 127.0.0.1 | head -1); echo "private=$PRIVATE_IP"

# enable the service
sudo sed -i 's/#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/' /etc/default/coturn
```

Copy `docs/turnserver.conf` from this repo to the VM as `/etc/turnserver.conf`, then edit it:
- replace `PUBLIC_IP` and `PRIVATE_IP` with the values printed above,
- set a strong password in the `user=babycam:...` line.

```bash
sudo nano /etc/turnserver.conf      # paste + edit
sudo systemctl enable coturn
sudo systemctl restart coturn
sudo systemctl status coturn        # should be active (running)
```

## 4. Also open the OS firewall (second firewall)

Oracle Ubuntu images ship with locked-down iptables. Open the same ports at the OS level:

```bash
sudo iptables -I INPUT -p udp --dport 3478 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 3478 -j ACCEPT
sudo iptables -I INPUT -p udp --dport 5349 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 5349 -j ACCEPT
sudo iptables -I INPUT -p udp --dport 49152:65535 -j ACCEPT
sudo netfilter-persistent save      # persist across reboots (install iptables-persistent if missing)
```

## 5. Test it

From any machine (e.g. your Mac), use the WebRTC Trickle ICE page:
https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/

- Add `turn:PUBLIC_IP:3478`, username `babycam`, password = the one you set.
- Click "Gather candidates". You should see a candidate of type **`relay`**.
- If you see `relay`, TURN works. If only `host`/`srflx`, a firewall port is still closed.

## 6. Hand off to the app

Send the maintainer:
- TURN URL: `turn:PUBLIC_IP:3478` (UDP) and `turn:PUBLIC_IP:3478?transport=tcp`
- username: `babycam`
- password: (what you set)

They replace the Metered entries in `WebRtcSession.iceServers()` with these.

---

## Later (optional but recommended): TURN over TLS on 443

Some very strict networks only allow HTTPS (443). To traverse those, add a domain + free
Let's Encrypt cert and enable `turns:YOURDOMAIN:443?transport=tcp`:

1. Point a domain/subdomain (e.g. `turn.yourdomain.com`) A-record at the VM's public IP. (Free
   domains: DuckDNS, or any you own.)
2. `sudo apt install certbot && sudo certbot certonly --standalone -d turn.yourdomain.com`
   (open TCP 80 temporarily for the challenge).
3. In `/etc/turnserver.conf`: remove `no-tls`/`no-dtls`, uncomment the `cert=`/`pkey=` lines pointing
   at the Let's Encrypt files, and set `tls-listening-port=443` (open 443 in both firewalls).
4. `sudo systemctl restart coturn`. Add `turns:turn.yourdomain.com:443?transport=tcp` to the app.
