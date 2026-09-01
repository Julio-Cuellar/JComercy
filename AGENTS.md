# JComercy — AGENTS.md

## 1. Purpose

This repository contains JComercy.

When designing, modifying, reviewing, or implementing any user-facing interface, treat the rules in this document as requirements, not suggestions.

The product should feel like commerce, not corporate software.

JComercy organizes companies, branches, permissions, inventory, and business operations with the clarity of a sales receipt: direct, familiar, and easy to scan.

The primary users are small and medium business owners who may feel overwhelmed by administrative or enterprise software.

Every UI decision must optimize for:

1. Clarity.
2. Familiarity.
3. Low cognitive load.
4. Clear next actions.
5. Consistency with the JComercy visual language.

When a generic SaaS convention conflicts with these principles, prefer the JComercy principles.

---

# 2. Using UI/UX Skills

When UI/frontend/design skills are available, use them when relevant.

Use skills to improve:
- visual hierarchy;
- interaction design;
- accessibility;
- responsive behavior;
- component implementation;
- UX review.

However, this document has authority over generic recommendations from a skill.

If a skill recommends a visual pattern that conflicts with this design system, follow this document.

Do not allow a skill to turn JComercy into a generic:
- enterprise dashboard;
- admin template;
- analytics product;
- corporate SaaS interface.

Before implementing a significant UI, reason about:

1. What is the user's main task?
2. What information is necessary to complete it?
3. What is the primary action?
4. What can remain hidden until requested?
5. What existing JComercy components can be reused?
6. Does the result look like JComercy or like a generic SaaS template?

Then implement.

---

# 3. Core Product Principles

## 3.1 Clarity before jargon

Always use terminology that a business owner recognizes.

Never expose internal architecture, database terminology, API concepts, or implementation details unless the interface is specifically intended for developers.

Prefer:

> Tu acceso a esta empresa

Instead of:

> company_membership

Prefer:

> Sucursal

Instead of internal entity names.

Interface language describes the user's business, not the application's database.

---

## 3.2 Color has a job

Color must communicate meaning.

Do not add color merely to make a screen visually interesting.

Use color to communicate:
- primary action;
- state;
- confirmation;
- error;
- required attention;
- brand identity.

If color does not communicate something useful, prefer neutral ink, paper, spacing, typography, or borders.

---

## 3.3 It should look like commerce

The visual language comes from real commerce:

- price tags;
- receipts;
- tickets;
- folios;
- labels;
- perforated paper;
- physical business artifacts.

Use these references subtly.

Do not turn them into decorative gimmicks.

The product should evoke a modern commerce tool, not a literal cash register.

---

## 3.4 Never overwhelm

Show what is needed for the current task.

Advanced information should be progressively disclosed.

Do not place every possible:
- action;
- filter;
- metric;
- setting;
- status;
- configuration

on the screen simultaneously.

If something is secondary, move it behind:
- progressive disclosure;
- a detail view;
- an expandable section;
- a contextual menu;
- a secondary flow.

A simple screen with one obvious next action is preferable to a feature-dense dashboard.

---

# 4. Visual Direction

JComercy should feel:

- warm;
- approachable;
- practical;
- modern;
- trustworthy;
- lightweight;
- commerce-oriented.

JComercy should NOT feel:

- corporate;
- bureaucratic;
- overly technical;
- futuristic;
- financial-enterprise;
- generic SaaS;
- overly playful;
- visually noisy.

Avoid the stereotypical AI-generated SaaS aesthetic.

In particular, avoid:

- excessive cards;
- excessive rounded containers;
- gradients without semantic purpose;
- glassmorphism;
- glowing elements;
- decorative charts;
- huge hero typography inside application screens;
- excessive shadows;
- random colored icons;
- unnecessary badges;
- excessive use of dashboard KPI cards.

Hierarchy should come primarily from:

1. typography;
2. spacing;
3. alignment;
4. borders;
5. surfaces;
6. color.

Do not use a card when spacing or a divider would communicate the grouping just as well.

---

# 5. Color System

Use these exact design tokens.

```css
:root {
  --tinta: #22343E;

  --papel: #F5FAF9;
  --papel-alto: #FFFFFF;

  --azul: #4C9BE8;
  --azul-suave: rgba(76, 155, 232, 0.14);

  --verde: #3FBF8F;
  --verde-suave: rgba(63, 191, 143, 0.14);

  --menta: #8FF0D6;
  --menta-suave: rgba(143, 240, 214, 0.28);

  --rojo: #E5544A;
  --rojo-suave: rgba(229, 84, 74, 0.12);

  --linea: #DFEEEA;
  --linea-fuerte: #C9E1DA;

  --radio: 16px;
  --radio-chico: 10px;

  --display: "Baloo 2", sans-serif;
  --sans: "Nunito Sans", -apple-system, sans-serif;
  --mono: "IBM Plex Mono", monospace;
}
```

Do not introduce new brand colors casually.

If a new semantic color appears necessary, first determine whether the meaning can be communicated using an existing token.

---

# 6. Color Rules

## Blue — `#4C9BE8`

Blue is the primary brand and action color.

Use for:
- primary actions;
- brand identity;
- logo;
- pending states;
- focus borders;
- selected interactive elements when appropriate.

There should normally be only ONE primary blue action per view.

Do not create multiple competing primary buttons.

---

## Green — `#3FBF8F`

Green exclusively communicates positive or active states.

Use for:
- active;
- success;
- completed positive states;
- confirmations.

Do NOT use green as arbitrary decoration.

Do NOT use green as an alternative action color.

---

## Mint — `#8FF0D6`

Mint is a supporting background color.

Use for:
- soft backgrounds;
- visual breathing room;
- subtle highlights;
- selected decorative surfaces.

Never use mint as body text.

Never rely on mint alone to communicate status.

Do not place normal text directly in mint when contrast is insufficient.

---

## Red — `#E5544A`

Red is reserved for:
- errors;
- blocked states;
- destructive consequences;
- expiration where action is required.

Never use red merely to attract attention.

---

## Ink — `#22343E`

Use Ink for:
- primary text;
- strong UI text;
- dark surfaces;
- navigation/sidebar surfaces when applicable.

Never default to pure black.

---

## Paper

`#F5FAF9` is the default application background.

`#FFFFFF` is the elevated surface.

Prefer these surfaces over arbitrary neutral grays.

---

# 7. Maximum Color Density

A screen should normally contain no more than three vivid brand colors simultaneously:

- blue;
- green;
- one supporting semantic/accent color.

Do not make every component colorful.

Neutral UI is intentional.

Color should become noticeable precisely because most of the interface is restrained.

---

# 8. Typography

Use:

### Display
Baloo 2

Weights:
- 500;
- 600;
- 700.

Use for:
- page titles;
- section titles;
- important display text.

Default heading weights should be 600–700.

### Interface and body
Nunito Sans

Weights:
- 400;
- 600;
- 700.

Use 400 for normal reading.

Use 600–700 selectively for:
- labels;
- buttons;
- important values;
- emphasis.

### Data
IBM Plex Mono

Weights:
- 400;
- 500.

ONLY use IBM Plex Mono for actual identifiers and code-like business data, such as:

- `SUC-001`;
- `ORD-2948`;
- company IDs;
- folios;
- codes;
- technical identifiers when they genuinely need to be visible.

Never use monospace as decoration.

Never use IBM Plex Mono for paragraphs, labels, navigation, buttons, headings, or general UI text.

---

# 9. Typography Rules

Never use ALL CAPS for:
- labels;
- buttons;
- headings;
- navigation.

Prefer sentence case.

Avoid excessive font weights.

Avoid using size alone to create hierarchy.

Prefer a combination of:
- font family;
- weight;
- spacing;
- position;
- restrained size differences.

Application screens should not use oversized marketing-style headings.

---

# 10. Logo

The JComercy symbol is a price tag.

Use this canonical SVG geometry:

```svg
<svg viewBox="0 0 32 32">
  <rect
    x="7"
    y="7"
    width="18"
    height="18"
    rx="5"
    transform="rotate(45 16 16)"
    fill="#4C9BE8"
  />
  <circle
    cx="16"
    cy="9.5"
    r="2"
    fill="#F5FAF9"
  />
</svg>
```

Logo requirements:

- tag color is always `#4C9BE8`;
- minimum interface height is 20px;
- preserve clear space equal to approximately half the tag width;
- maintain the canonical rotation;
- never stretch the logo.

Never:
- add gradients;
- add shadows;
- recolor the tag;
- make it multicolor;
- change the rotation;
- use decorative effects.

The eyelet uses:

- `#F5FAF9` on light contexts;
- `#22343E` when required on dark brand contexts.

---

# 11. Buttons

## Primary

Primary buttons use:
- background: `--azul`;
- white text;
- pill shape;
- `border-radius: 999px`.

A view should have ONE visually dominant primary action.

Examples:

> Guardar cambios

> Agregar cliente

> Crear sucursal

Do not use generic labels such as:

> Enviar

when a more specific action exists.

---

## Secondary

Secondary buttons use:
- transparent background;
- Ink border;
- Ink text.

On hover they may invert to:
- Ink background;
- light text.

Secondary actions must remain visually subordinate to the primary action.

---

## Ghost

Ghost buttons:
- have no border;
- have no filled background by default;
- use text/icon emphasis only.

Use for low-emphasis actions such as:

> Ver detalle

---

# 12. Status Pills

Use status pills consistently.

## Active

Background:

```css
rgba(63,191,143,.14)
```

Text:

```css
#1F7A56
```

Label:

> Activo

---

## Pending

Background:

```css
rgba(76,155,232,.14)
```

Text:

```css
#2E6FA8
```

Label:

> Pendiente

---

## Blocked

Background:

```css
rgba(229,84,74,.12)
```

Text:

```css
#E5544A
```

Label:

> Bloqueado

---

## Read only

Background:

```css
rgba(34,52,62,.07)
```

Text:
Ink at approximately 70%.

Label:

> Solo lectura

Do not invent a unique pill color for every possible state.

---

# 13. Forms

Inputs use:

```css
border: 2px solid var(--linea-fuerte);
border-radius: 10px;
```

On focus:
- border becomes `--azul`;
- no glow;
- no decorative box shadow.

Labels must always remain visible above the input.

Never use placeholder text as the only label.

Placeholder text should provide an example or hint, not identify the field.

Validation messages should explain:
1. what needs attention;
2. how to fix it.

---

# 14. Surfaces

Default page background:

```css
var(--papel)
```

Elevated surface:

```css
var(--papel-alto)
```

Cards and containers use:

```css
border-radius: 16px;
```

Inputs use:

```css
border-radius: 10px;
```

Do not arbitrarily mix radius values.

---

# 15. Surface Depth

Never show more than two elevated surface levels simultaneously.

Acceptable:

```text
Page
└── Card
```

or:

```text
Page
└── Panel
    └── Card
```

Avoid:

```text
Page
└── Panel
    └── Card
        └── Inner card
            └── Nested card
```

If several nested surfaces appear necessary, reconsider the information architecture.

---

# 16. Borders and Ticket Language

Standard separation uses:

```css
var(--linea)
```

Stronger boundaries use:

```css
var(--linea-fuerte)
```

Solid borders are the default.

Dashed borders have a specific brand meaning.

Use dashed borders ONLY when deliberately referencing receipt perforation, particularly in:
- company cards;
- branch cards;
- receipt/ticket-inspired elements;
- specific typography specimens where appropriate.

Do not use dashed borders as a generic visual pattern.

The receipt metaphor should be recognizable but restrained.

---

# 17. Icons

Icons support meaning; they do not decorate empty space.

Use a single icon family throughout the application whenever possible.

Icons should:
- have consistent stroke weight;
- align optically with labels;
- remain secondary to text when the action may be ambiguous.

Do not create colorful icon tiles merely to decorate cards.

Do not use an icon instead of a label when the meaning may not be obvious to a small-business owner.

---

# 18. Cards

Cards are not the default solution for grouping content.

Before creating a card, ask:

> Would spacing, typography, alignment, or a divider communicate this hierarchy equally well?

If yes, do not create the card.

Use cards when there is a meaningful conceptual object or elevated surface, such as:
- a company;
- a branch;
- a ticket-like summary;
- a focused workflow.

Avoid grids of generic cards containing:
- icon;
- title;
- description;
- button

unless the content genuinely represents separate selectable objects.

---

# 19. Tables and Business Data

Tables should prioritize scanning.

Use:
- clear column hierarchy;
- restrained borders;
- strong alignment;
- readable spacing;
- semantic status pills.

Avoid:
- excessive vertical borders;
- excessive badges;
- colored backgrounds on every row;
- unnecessary action buttons in every cell.

IDs and folios may use IBM Plex Mono.

Human-readable names should never use monospace.

Primary business information should visually dominate system metadata.

---

# 20. Navigation

Navigation should reflect the mental model of the business owner.

Organize around concepts such as:
- empresas;
- sucursales;
- clientes;
- inventario;
- ventas;
- equipo;
- configuración.

Do not organize navigation around backend services or database entities.

Keep the primary navigation short.

Advanced configuration belongs deeper in the hierarchy.

---

# 21. Voice and Tone

JComercy speaks like one business owner explaining something clearly to another.

Voice is:
- direct;
- warm;
- practical;
- confident;
- non-technical.

Voice is NOT:
- bureaucratic;
- apologetic;
- robotic;
- overly casual;
- developer-oriented.

---

# 22. Action Labels

Use active voice.

A button must describe exactly what happens.

Prefer:

> Guardar cambios

Instead of:

> Enviar

Prefer:

> Agregar cliente

Instead of:

> Nuevo

when the object would otherwise be ambiguous.

Prefer:

> Activar módulo

Instead of:

> Confirmar

when activation is the actual action.

The terminology used for an action must remain consistent between:
- button;
- loading state;
- success confirmation;
- error recovery.

---

# 23. Errors

Errors must explain:
1. what happened;
2. what the user can do next.

Do not apologize excessively.

Do not expose technical errors unless the audience requires them.

Never show messages like:

> Hubo un error al procesar el webhook de sucursal.

Translate technical conditions into business consequences.

Prefer:

> No puedes vender inventario de otra sucursal sin pedirlo primero.

Avoid:

> Lo sentimos, algo salió mal.

Prefer specific recovery instructions.

Example:

> Esta sucursal no tiene módulos activos todavía. Actívalos aquí.

If technical debugging information is necessary, keep it secondary and separate from the primary user-facing message.

---

# 24. Empty States

An empty state is an invitation to take the next useful action.

Use:

> Aún no tienes clientes registrados.

Primary action:

> Agregar cliente

Do not treat empty states as errors.

Avoid:
- apologies;
- technical explanations;
- giant illustrations without purpose.

The empty state should answer:
1. What is missing?
2. Is that expected?
3. What should I do next?

---

# 25. Loading and Feedback

Every asynchronous action must have a deliberate state.

Consider:
- idle;
- loading;
- success;
- error;
- disabled.

Do not leave users wondering whether an action happened.

Success feedback should be short and concrete.

Example:

Action:

> Guardar cambios

Confirmation:

> Listo, guardado.

Avoid generic:

> Operación realizada exitosamente.

---

# 26. Progressive Disclosure

Advanced functionality should not compete with the primary task.

Use:
- expandable sections;
- contextual menus;
- detail views;
- secondary settings screens;
- advanced sections.

Do not show advanced controls merely because they exist.

Ask:

> Does the user need this to complete today's task?

If not, consider hiding it until requested.

---

# 27. Accessibility

Accessibility is a requirement.

Always verify:
- sufficient color contrast;
- keyboard navigation;
- visible focus states;
- semantic HTML;
- associated form labels;
- accessible names for icon-only controls;
- meaningful error messaging;
- status not communicated by color alone;
- reasonable touch targets.

Target WCAG AA contrast.

Mint must never be relied upon for readable foreground text.

Status must combine color with text or another semantic indicator.

---

# 28. Responsive Behavior

Every interface must be intentionally usable on:
- desktop;
- tablet;
- mobile.

Do not treat mobile as a compressed desktop.

When space decreases:
1. preserve the primary task;
2. preserve important business information;
3. move secondary actions into contextual UI;
4. stack content when necessary;
5. simplify secondary metadata.

Never solve responsive design by hiding critical information.

Tables require deliberate mobile behavior.

Depending on the task, consider:
- horizontal scrolling;
- priority columns;
- responsive rows;
- detail views.

Choose based on usability, not convenience of implementation.

---

# 29. Interaction and Motion

Motion should clarify:
- state change;
- hierarchy;
- navigation;
- feedback.

Do not animate merely to make the interface feel modern.

Avoid:
- constant motion;
- bouncing elements;
- decorative floating effects;
- exaggerated spring animations.

Prefer short, subtle transitions.

Respect reduced-motion preferences.

---

# 30. New Component Decision Rule

Before creating a new UI component:

1. Search for an existing equivalent.
2. Determine whether the existing component can be extended.
3. Check consistency with this design system.
4. Create a new component only when there is a reusable product concept.

Do not duplicate components with slightly different styling.

If using an external component library, treat it as implementation infrastructure, not visual direction.

External components must be adapted to JComercy.

Never allow the default appearance of a UI library to define the product's identity.

---

# 31. Implementation Rules

When implementing UI:

- use the existing project stack;
- reuse existing tokens;
- reuse existing components;
- preserve established application patterns when compatible with this document;
- do not introduce a new UI library without a clear need;
- do not hardcode brand values repeatedly when a token exists;
- centralize reusable design decisions.

Prefer semantic tokens over arbitrary values.

Prefer:

```css
color: var(--tinta);
```

over:

```css
color: #22343E;
```

inside application components.

Do not introduce arbitrary colors because they are available in Tailwind or another framework.

---

# 32. Generic SaaS Anti-Patterns

Actively avoid these patterns unless the product task genuinely requires them:

- four KPI cards at the top of every page;
- icon inside colored square + title + metric;
- every section inside a card;
- excessive sidebar nesting;
- gradients used as decoration;
- purple/blue AI gradients;
- glassmorphism;
- enormous border radii everywhere;
- random shadows;
- random badges;
- tiny gray text everywhere;
- excessive muted text;
- every secondary action visible simultaneously;
- generic empty-state illustrations;
- decorative monospace;
- unnecessary charts;
- huge page titles;
- floating blobs;
- excessive pill-shaped UI.

Pill shape is strongly associated with JComercy's primary buttons and appropriate compact controls.

Do not turn every container into a pill.

---

# 33. JComercy Visual Signature

When appropriate, reinforce the brand using a restrained combination of:

- Paper background;
- Ink typography;
- Baloo 2 headings;
- blue primary actions;
- subtle mint breathing room;
- price-tag logo geometry;
- receipt-like alignment;
- folio typography for genuine identifiers;
- deliberate dashed perforation details;
- clear transactional language.

The signature should emerge from the system as a whole.

Do not place receipt decorations everywhere simply to make the brand recognizable.

---

# 34. Screen Design Process

For every significant new screen or redesign, follow this sequence.

## Step 1 — Identify the job

State internally what the user is trying to accomplish.

Example:

> The owner needs to understand which branches can currently sell.

Do not begin with visual components.

---

## Step 2 — Identify essential information

Determine what the user needs to know to make the next decision.

Separate:
- essential;
- secondary;
- advanced.

---

## Step 3 — Identify the primary action

There should normally be one obvious primary action.

If there appear to be three primary actions, reconsider the hierarchy.

---

## Step 4 — Design hierarchy

Establish hierarchy using:

1. layout;
2. typography;
3. spacing;
4. alignment;
5. borders;
6. surfaces;
7. color.

Do not start by adding cards.

---

## Step 5 — Apply JComercy language

Ask whether opportunities exist to naturally use:
- ticket;
- label;
- folio;
- receipt;
- business-object metaphors.

Use only when they improve comprehension or reinforce identity without adding noise.

---

## Step 6 — Implement states

For relevant components implement:
- default;
- hover;
- focus;
- active;
- disabled;
- loading;
- success;
- error;
- empty.

Do not consider a component complete if only its ideal state exists.

---

## Step 7 — Test responsive behavior

Verify the primary workflow at narrow widths.

Ensure hierarchy survives without simply removing essential information.

---

## Step 8 — Review accessibility

Check:
- contrast;
- focus;
- keyboard behavior;
- labels;
- semantic markup;
- status communication.

---

## Step 9 — Remove unnecessary UI

Before finishing, ask:

> What can be removed without harming the user's task?

Simplify.

---

# 35. UI Review Checklist

Before considering UI work complete, verify:

### Brand
- [ ] Does this feel like JComercy?
- [ ] Does it avoid generic corporate SaaS aesthetics?
- [ ] Are commerce references subtle rather than decorative?

### Hierarchy
- [ ] Is the primary task obvious?
- [ ] Is there one dominant primary action?
- [ ] Is advanced information progressively disclosed?
- [ ] Can any card/container be removed?

### Color
- [ ] Is blue used for the primary action?
- [ ] Is green only communicating positive/active state?
- [ ] Is red only communicating errors/blocking/destructive meaning?
- [ ] Is mint being used as a background rather than readable text?
- [ ] Are there no unnecessary arbitrary colors?
- [ ] Are there at most roughly three vivid colors competing on screen?

### Typography
- [ ] Are headings using Baloo 2?
- [ ] Is interface/body copy using Nunito Sans?
- [ ] Is IBM Plex Mono restricted to genuine codes/folios/IDs?
- [ ] Is ALL CAPS avoided?

### Components
- [ ] Are primary buttons blue pills?
- [ ] Are inputs 10px radius with visible labels?
- [ ] Are container radii consistent at 16px?
- [ ] Are status pills using established semantic colors?
- [ ] Are dashed borders used only as deliberate ticket/perforation details?

### Content
- [ ] Does the UI use business language instead of technical language?
- [ ] Do buttons describe their exact action?
- [ ] Do errors explain what happened and what to do?
- [ ] Do empty states suggest the next action?
- [ ] Is terminology consistent across the workflow?

### UX
- [ ] Are loading states implemented?
- [ ] Are error states implemented?
- [ ] Are empty states implemented where applicable?
- [ ] Are disabled states understandable?
- [ ] Does the UI avoid unnecessary cognitive load?

### Accessibility
- [ ] Does text have sufficient contrast?
- [ ] Is focus visible?
- [ ] Can the workflow be completed with keyboard navigation?
- [ ] Do form controls have labels?
- [ ] Is status communicated with more than color?

### Responsive
- [ ] Does the primary task work on mobile?
- [ ] Is important information preserved?
- [ ] Do tables have deliberate responsive behavior?
- [ ] Are touch targets usable?

---

# 36. Final Self-Review

After implementing a significant UI change, perform a final design review before declaring the task complete.

Ask:

1. Does this look like JComercy?
2. Would a small-business owner understand the screen immediately?
3. Is there unnecessary technical terminology?
4. Is the primary action obvious?
5. Is there unnecessary visual noise?
6. Did I use color only where it communicates meaning?
7. Did I create unnecessary cards?
8. Are all relevant states covered?
9. Does it work responsively?
10. Is it accessible?
11. Did an external component library introduce generic styling?
12. Could I remove something and make the screen clearer?

If the interface resembles a generic SaaS/admin template more than a commerce product, revise it before finishing.

---

# 37. Conflict Resolution

When instructions conflict, use this priority:

1. Explicit requirements in the current user/task request.
2. JComercy brand and UX rules in this document.
3. Existing JComercy design-system patterns.
4. Accessibility and usability best practices.
5. Recommendations from installed UI/frontend skills.
6. Defaults from external component libraries.

Never violate accessibility merely to reproduce a visual detail.

If a requested design conflicts with accessibility, preserve the brand intent while implementing an accessible alternative.

---

# 38. Definition of Done for UI Work

UI work is not complete merely because it renders.

A UI task is complete when:

- the primary workflow works;
- the result follows JComercy visual language;
- content uses JComercy voice;
- relevant states exist;
- responsive behavior is intentional;
- accessibility has been considered;
- existing components and tokens are reused where appropriate;
- unnecessary visual complexity has been removed;
- no obvious generic SaaS styling has leaked into the final result.

JComercy's goal is not to look like software made for administrators.

It should feel like a tool made for people running a real business.