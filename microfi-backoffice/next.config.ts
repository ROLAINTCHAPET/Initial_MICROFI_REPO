import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Next's dev server only accepts requests from `localhost` by default and silently blocks
  // everything else (including API route POSTs like /api/auth/login) — needed to reach the
  // dev server from another machine on the LAN, or through a tunnel like ngrok.
  allowedDevOrigins: ["192.168.0.158", "*.ngrok-free.app"],
};

export default nextConfig;
