---
name: High-Utility Financial Core
colors:
  surface: '#f7fafd'
  surface-dim: '#d7dadd'
  surface-bright: '#f7fafd'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f1f4f7'
  surface-container: '#ebeef1'
  surface-container-high: '#e5e8eb'
  surface-container-highest: '#e0e3e6'
  on-surface: '#181c1e'
  on-surface-variant: '#43474d'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eef1f4'
  outline: '#74777e'
  outline-variant: '#c4c6ce'
  surface-tint: '#49607e'
  primary: '#000f22'
  on-primary: '#ffffff'
  primary-container: '#0a2540'
  on-primary-container: '#768dad'
  inverse-primary: '#b0c8eb'
  secondary: '#006b59'
  on-secondary: '#ffffff'
  secondary-container: '#55fcd8'
  on-secondary-container: '#00725f'
  tertiary: '#170d00'
  on-tertiary: '#ffffff'
  tertiary-container: '#322100'
  on-tertiary-container: '#b68200'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d2e4ff'
  primary-fixed-dim: '#b0c8eb'
  on-primary-fixed: '#001c37'
  on-primary-fixed-variant: '#314865'
  secondary-fixed: '#55fcd8'
  secondary-fixed-dim: '#27dfbc'
  on-secondary-fixed: '#002019'
  on-secondary-fixed-variant: '#005142'
  tertiary-fixed: '#ffdea8'
  tertiary-fixed-dim: '#ffba20'
  on-tertiary-fixed: '#271900'
  on-tertiary-fixed-variant: '#5e4200'
  background: '#f7fafd'
  on-background: '#181c1e'
  surface-variant: '#e0e3e6'
typography:
  display:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  h1:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
  h2:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  label-bold:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.01em
  caption:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  touch-target: 48px
  gutter: 16px
  margin-mobile: 20px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 24px
---

## Brand & Style
The design system is engineered for **Microfi**, a high-stakes financial utility operating in the CEMAC region. The brand personality is rooted in institutional stability and unwavering reliability, functioning as a "digital vault" for cash collection. 

The aesthetic follows a **High-Contrast Corporate** style, prioritized for field agents working in high-glare, outdoor environments. It minimizes decorative elements in favor of structural clarity, using heavy ink-weights and distinct boundary lines to ensure legibility for users with varying literacy levels. The emotional response is one of safety, speed, and precision.

## Colors
The palette is built on high-luminance contrast ratios to combat sunlight glare. 

- **Primary (Deep Navy):** Used for headers, primary actions, and critical navigation to anchor the UI with a "bank-like" authority.
- **Secondary (Emerald Green):** Reserved exclusively for successful transaction states and positive balance indicators.
- **Tertiary (Amber):** High-visibility warning color for pending transactions or verification requirements.
- **Signal Red:** Immediate attention for failed collections or security alerts.

Backgrounds utilize a neutral off-white to reduce eye strain while maintaining a high contrast ratio against dark text and primary containers.

## Typography
This design system utilizes **Inter** exclusively to leverage its tall x-height and exceptional legibility on low-resolution mobile displays. 

To assist low-literacy users, the hierarchy relies on scale and weight rather than color alone. Numbers (transaction amounts) should always be rendered in **H1** or **Display** sizes with medium or bold weights to ensure they are the first element scanned on any screen. Use sentence case for all labels to improve readability.

## Layout & Spacing
The layout follows a strict 4px baseline grid. On mobile devices, a 20px outer margin is required to ensure content remains clear of phone cases and thumb-grips.

**Touch Targets:** Every interactive element (buttons, checkboxes, list items) must adhere to a minimum height of **48dp**. This is non-negotiable for field-use accessibility. 

**Grid:** Use a fluid single-column layout for transaction flows to keep the user focused on one task at a time. Multi-column layouts are reserved for tablet-based dashboard views using a 12-column grid with 24px gutters.

## Elevation & Depth
This design system avoids complex shadows or blurs that wash out in direct sunlight. Instead, it uses **Low-Contrast Outlines** and **Tonal Layering**.

- **Level 0 (Surface):** The base background (#F4F7FA).
- **Level 1 (Cards/Containers):** Pure white (#FFFFFF) with a 1px solid border (#E1E8F0).
- **Level 2 (Active/Floating):** Use a high-density, small-radius shadow (4px blur, 10% opacity) only for floating action buttons (FAB) to distinguish them from the flat layout.

Depth is primarily communicated through the layering of containers, where active inputs or focused cards receive a 2px stroke in the Primary color.

## Shapes
The shape language is "Soft-Geometric." It maintains the professional rigor of a bank while appearing modern and approachable.

- **Radius-sm (6px):** Applied to all form inputs, text fields, and small tags. This keeps the inputs feeling precise and structured.
- **Radius-md (12px):** Applied to all cards, modal sheets, and main action buttons. This larger radius helps distinguish major content blocks from individual UI controls.

## Components

### Buttons
Primary buttons must be full-width on mobile (320px+) with a 48px height. Use the Primary Navy background with white text. Secondary buttons should use a 2px Primary Navy stroke with no fill.

### Input Fields
Inputs must have a 1px border (#CBD5E0) that thickens to 2px Primary Navy on focus. Labels must always be visible (no floating labels that disappear) to assist users who may lose track of their progress.

### Transaction Cards
Cards should use the 12px radius. To aid low-literacy users, use large iconography (24px) paired with the Secondary (Green) or Red colors to indicate the transaction direction (Inbound vs Outbound) at a glance.

### List Items
Each row in a list must be at least 64px tall to provide ample space for a 48px touch target. Use a light separator line (#E1E8F0) between items.

### Status Chips
Small pills with 100px radius. Use highly saturated backgrounds with high-contrast text (e.g., White text on Emerald Green) for immediate status recognition in outdoor settings.