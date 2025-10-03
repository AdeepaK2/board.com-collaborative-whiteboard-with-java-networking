# Deployment Guide for DigitalOcean

This guide explains how to deploy your Whiteboard application on DigitalOcean using different methods.

## Option 1: DigitalOcean App Platform (Recommended)

### Prerequisites
- GitHub repository with your code
- DigitalOcean account

### Steps:
1. **Push your code to GitHub** (already done: `AdeepaK2/board.com-collaborative-whiteboard-with-java-networking`)

2. **Create App on DigitalOcean:**
   - Go to [DigitalOcean App Platform](https://cloud.digitalocean.com/apps)
   - Click "Create App"
   - Connect your GitHub repository
   - Import the `.do/app.yaml` configuration file

3. **Configure Environment Variables:**
   - Backend: `PORT=8080`
   - Frontend: `VITE_WEBSOCKET_URL=wss://your-backend-url.ondigitalocean.app`

4. **Deploy:**
   - Review settings and click "Create Resources"
   - Wait for deployment (5-10 minutes)

### Estimated Cost: $12-24/month

## Option 2: DigitalOcean Droplets with Docker

### Prerequisites
- DigitalOcean account
- Docker knowledge

### Steps:
1. **Create a Droplet:**
   - Choose Ubuntu 22.04 LTS
   - Basic plan: $6/month
   - Enable Docker during setup

2. **SSH into your Droplet:**
   ```bash
   ssh root@your-droplet-ip
   ```

3. **Clone your repository:**
   ```bash
   git clone https://github.com/AdeepaK2/board.com-collaborative-whiteboard-with-java-networking.git
   cd board.com-collaborative-whiteboard-with-java-networking
   ```

4. **Build and run with Docker Compose:**
   ```bash
   docker-compose up -d
   ```

5. **Configure domain (optional):**
   - Point your domain to the droplet IP
   - Set up SSL with Let's Encrypt

### Estimated Cost: $6-12/month

## Option 3: Manual Deployment on Droplet

### Steps:
1. **Create Ubuntu 22.04 Droplet** ($6/month)

2. **Install Java and Node.js:**
   ```bash
   # Install Java 11
   sudo apt update
   sudo apt install openjdk-11-jdk

   # Install Node.js
   curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
   sudo apt-get install -y nodejs

   # Install nginx
   sudo apt install nginx
   ```

3. **Deploy Backend:**
   ```bash
   # Clone repository
   git clone https://github.com/AdeepaK2/board.com-collaborative-whiteboard-with-java-networking.git
   cd board.com-collaborative-whiteboard-with-java-networking

   # Compile Java application
   mkdir -p target/classes
   javac --release 11 -cp "src/main/java" -d target/classes src/main/java/org/example/server/WebSocketWhiteboardServerSimple.java

   # Run with systemd service (create service file)
   sudo java -cp target/classes org.example.server.WebSocketWhiteboardServerSimple
   ```

4. **Deploy Frontend:**
   ```bash
   cd frontend
   npm install
   npm run build

   # Copy build files to nginx
   sudo cp -r dist/* /var/www/html/
   sudo systemctl restart nginx
   ```

## Environment Variables for Production

### Frontend (.env.production):
```
VITE_WEBSOCKET_URL=wss://your-backend-domain.com:8080
```

### Backend:
```
PORT=8080
```

## Domain Setup

1. **Buy a domain** (optional but recommended)
2. **Point DNS to your DigitalOcean resources:**
   - A record: `@` → your-app-ip or app-platform-url
   - A record: `www` → your-app-ip or app-platform-url

## SSL Certificate

### For App Platform:
- Automatic SSL with custom domains

### For Droplets:
```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx

# Get SSL certificate
sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com
```

## Monitoring and Logs

### App Platform:
- Built-in metrics and logs in DigitalOcean dashboard

### Droplets:
```bash
# View application logs
sudo journalctl -u your-app-service -f

# Monitor system resources
htop
```

## Scaling

### App Platform:
- Automatic scaling based on demand
- Can increase instance count in settings

### Droplets:
- Resize droplet for more resources
- Add load balancer for multiple droplets

## Troubleshooting

### Common Issues:
1. **WebSocket connection fails:**
   - Check firewall settings (port 8080)
   - Verify WebSocket URL format (wss:// for HTTPS)

2. **Build failures:**
   - Ensure Java 11+ compatibility
   - Check Node.js version (18+)

3. **CORS issues:**
   - Add proper CORS headers in Java backend
   - Configure proxy in production

### Support:
- DigitalOcean Community: https://www.digitalocean.com/community
- Documentation: https://docs.digitalocean.com/

## Estimated Total Monthly Costs:
- **App Platform**: $12-24/month (easiest)
- **Droplet + Domain**: $6-15/month (more control)
- **Domain**: $10-15/year (optional)