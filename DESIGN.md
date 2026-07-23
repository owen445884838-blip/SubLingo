---
name: SubLingo Soft-Play
colors:
  surface: '#fdf7ff'
  surface-dim: '#ded8e3'
  surface-bright: '#fdf7ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f7f1fd'
  surface-container: '#f2ecf7'
  surface-container-high: '#ece6f2'
  surface-container-highest: '#e6e0ec'
  on-surface: '#1c1a22'
  on-surface-variant: '#494553'
  inverse-surface: '#322f38'
  inverse-on-surface: '#f5eefa'
  outline: '#7a7585'
  outline-variant: '#cac4d6'
  surface-tint: '#6545c9'
  primary: '#6342c6'
  on-primary: '#ffffff'
  primary-container: '#7c5de1'
  on-primary-container: '#fffbff'
  inverse-primary: '#ccbdff'
  secondary: '#755b00'
  on-secondary: '#ffffff'
  secondary-container: '#fecb30'
  on-secondary-container: '#705600'
  tertiary: '#286746'
  on-tertiary: '#ffffff'
  tertiary-container: '#42815d'
  on-tertiary-container: '#f6fff5'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e7deff'
  primary-fixed-dim: '#ccbdff'
  on-primary-fixed: '#1f005f'
  on-primary-fixed-variant: '#4d28b0'
  secondary-fixed: '#ffdf91'
  secondary-fixed-dim: '#f2c023'
  on-secondary-fixed: '#241a00'
  on-secondary-fixed-variant: '#594400'
  tertiary-fixed: '#aff1c6'
  tertiary-fixed-dim: '#93d5ac'
  on-tertiary-fixed: '#002111'
  on-tertiary-fixed-variant: '#0b5132'
  background: '#fdf7ff'
  on-background: '#1c1a22'
  surface-variant: '#e6e0ec'
  background-cream: '#fdfaf0'
  surface-purple-light: '#f0eaff'
  status-processing-pink: '#FFC6DA'
  status-processing-orange: '#FFCDA5'
  text-main: '#2e303a'
  text-muted: '#747688'
  progress-pink-fill: '#A57887'
  progress-orange-fill: '#A87E5E'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '700'
    lineHeight: '1.4'
  title-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '700'
    lineHeight: '1.4'
  body-md:
    fontFamily: Noto Sans SC
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '500'
    lineHeight: '1.2'
  label-xs:
    fontFamily: Plus Jakarta Sans
    fontSize: 10px
    fontWeight: '700'
    lineHeight: '1.0'
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  container-padding: 1.25rem
  stack-gap-lg: 2.5rem
  stack-gap-md: 1rem
  element-gap-sm: 0.5rem
  section-margin: 2.5rem
---

## Brand & Style
SubLingo is a language learning platform that positions itself as an approachable, friendly, and low-friction companion for video-based immersion. The brand personality is **optimistic, energetic, and nurturing**. 

The design style is a blend of **Modern Soft-Play and Tonal Layering**. It moves away from corporate rigidity by using high-saturation "candy" accents (yellow, purple, pink) against a warm, cream-based neutral foundation. The aesthetic utilizes "Squircle" geometry and oversized interactive elements to evoke a sense of playfulness and ease of use, making the daunting task of language learning feel like a casual entertainment activity.

## Colors
The palette is built on a warm, off-white foundation (**#fdfaf0**) to reduce eye strain and feel more "organic" than pure white. 

- **Primary Purple (#8B6DF1):** Used for primary action containers and brand identity. It represents creativity and wisdom.
- **Secondary Gold (#F8C62A):** The "Action" color. High-contrast and energetic, used for the main Floating Action Button (FAB) and critical confirmation buttons.
- **Semantic Accents:** Processing states use a range of warm pastels (Pink #FFC6DA and Orange #FFCDA5) to differentiate active tasks without using alarming "alert" colors. 
- **Success Green (#A0E2B8):** A soft mint used for "Ready" badges.
- **Typography:** Uses a deep charcoal-blue (#2e303a) instead of pure black to maintain softness while ensuring high legibility.

## Typography
The system uses **Plus Jakarta Sans** for all UI-related text (headings, buttons, labels) to reinforce the friendly, modern aesthetic. For body text and content that includes Chinese characters, **Noto Sans SC** is used to ensure maximum readability and a clean, humanist feel.

Headlines use a bold weight with slightly tight letter-spacing to create a distinctive brand "voice" in titles. Labels and badges use uppercase or medium weights to create clear hierarchy against the softer body copy.

## Layout & Spacing
The layout follows a **Fluid Mobile-First** model with a safe margin of 20px (1.25rem) on either side. 

- **Vertical Rhythm:** Sections are separated by large 40px (2.5rem) gaps to give content room to "breathe" and prevent the interface from feeling cluttered.
- **Internal Spacing:** Components use generous internal padding (typically 1.5rem for cards) to maintain the "soft" feel.
- **Grid:** Video cards use a 2-column responsive grid with a 16px (1rem) gutter.
- **Bottom Navigation:** Fixed at 80px height to accommodate safe areas and provide a large touch target.

## Elevation & Depth
Depth is created primarily through **Tonal Separation** and **Soft Ambient Shadows** rather than traditional elevation.

- **Soft Shadows:** Cards and buttons use a very subtle, low-opacity shadow (e.g., `0 8px 20px rgba(0,0,0,0.05)`). The main FAB uses a colored shadow (`rgba(248,198,42,0.4)`) to appear as if it is glowing and floating above the surface.
- **Tonal Layers:** Surfaces are differentiated by background color changes (e.g., a light purple input field inside a deeper purple container).
- **Glassmorphism:** The bottom navigation bar uses a `95%` opacity with a `backdrop-blur-lg` effect to maintain context of the content scrolling beneath it while keeping navigation legible.

## Shapes
The shape language is defined by **Extreme Roundness (Pill-Style)**. 

- **Containers:** Large cards use a 32px (rounded-3xl equivalent) corner radius, creating a "squishy" and friendly appearance.
- **Inputs & Buttons:** All interactive elements like the URL input and primary buttons use fully rounded (pill) ends.
- **Media:** Image thumbnails within cards use a slightly smaller radius (24px) to create a nested "nested shape" harmony with their parent containers.

## Components

### Buttons
- **Primary Action:** Large, pill-shaped, using the Secondary Gold (#F8C62A) with bold text. Includes a slight scale-up transition on hover/touch.
- **FAB:** The "Add Video" button is the highest-priority element, using gold with a distinctive colored drop-shadow.

### Cards
- **Task Cards:** Use high-contrast pastel backgrounds. They include a horizontal progress bar at the bottom with a 50% opacity background of the same hue as the fill.
- **Video Cards:** White background with a soft shadow. Features a nested image container with a 4:3 aspect ratio and an absolute-positioned status badge in the top-right.

### Inputs
- **Search/URL Bar:** A composite component. A primary-colored pill container holds a lighter-tinted internal pill where the actual text input resides. This "double-rounded" look is a signature brand element.

### Chips & Badges
- **Status Badges:** Small, fully rounded pills with `label-xs` typography. Backgrounds are high-transparency versions of the status color (e.g., Mint for Ready, Soft Brown for count).

### Navigation
- **Bottom Bar:** High-blur, translucent background. The active state is indicated by a pill-shaped background "pill" behind the icon, utilizing the Secondary Gold color.