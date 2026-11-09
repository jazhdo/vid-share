# VidShare

## Description

This is a website for sharing videos. Just that, no ads, no payments, no whatever.

## Components

### Cloudflare D1 Database
> A few tables containing information on users, likes, shares, videos, followers, and comments

### Video
- Format: Simple mp4 bytes
- Storage: Cloudflare R2 object storage

### Backend 
> Spring Boot Java

### Cloudflare Worker
> For interacting with the Cloudflare D1 and R2 modules

### Auth
> A custom made auth using bcrypt for passwords and the "users" database for storing them

### Secure Variables
> Environment Variables

### Continued Auth State
> JSON Web Tokens from a external dependency

### Webpage Formatting
> Thymeleaf for changing fields depending on the page, ex. homepage, video watching page, search page, etc.

### Image Conversion (for profile pictures)
> Scrimage webp conversion (an external dependency)

## Supported client sizes
> Width: Minimum of 800px
> Height: Minimum of 700px

## Tested environments
- Firefox 158.0 Build #20261007201254 on macOS 15.7.3

## What it works on
So far only tested on firefox, might not work on other browsers.

## Links (because a reviewer wanted to see them here)
1. [Link 1](https://video.itsjaz.com) (Works probably)
2. [Link 2](https://vid-share-new.onrender.com) (Always works)
3. [Link 3](http://video.itsjaz.com:8080) (May be broken)

## How to use
1. Go to one of the above links
2. Play a video, search for a video, upload a video, and/or create a account
3. Done! Its not really a game, more of just a tool

## Screenshots of it working
TODO: TO be added

## Biggest Updates for Week 3
- Storage now saves! (Previously on render hosting the databases were wiped on every new startup)
- There's a new Cloudflare Worker!