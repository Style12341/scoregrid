package com.scoregrid.auth.auth.infrastructure.web;

record LoginRequest(String usernameOrEmail, String password) {}
