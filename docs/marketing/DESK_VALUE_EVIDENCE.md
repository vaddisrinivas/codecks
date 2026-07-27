# Desk Value Evidence

Captured on: July 27, 2026

Owner: Codecks maintainers

Next review: before the next public README claim refresh

## Scope

This note backs the README's "Use the screen already beside your computer"
section.

It does not claim:

- a market-wide average mouse price;
- a representative national desk-price average;
- that Codecks returns cash;
- that all phones already sit on a computer desk;
- that a touchscreen trackpad always replaces a mouse for all work.

## Sources

- Phone placement study:
  `https://citeseerx.ist.psu.edu/document?doi=16baf1a983217b965bd72b868086126e3e24634c&repid=rep1&type=pdf`
- Smartphone ownership:
  `https://www.pewresearch.org/chart/mobile-phone-ownership-2/`
- Logitech mice catalog:
  `https://www.logitech.com/en-us/shop/c/mice`
- Razer Gigantus V2 overview:
  `https://www.razer.com/gaming-mouse-mats/razer-gigantus-v2`
- Razer sampled SKU pages:
  `https://www.razer.com/gaming-mouse-mats/Razer-Gigantus-V2/RZ02-03330200-R3U1`
  `https://www.razer.com/gaming-mouse-mats/Razer-Gigantus-V2/RZ02-03330300-R3U1`
- IKEA desks catalog:
  `https://www.ikea.com/us/en/cat/desks-computer-desks-20649/`

## Inclusion rules

- Logitech: first 18 visible price entries from the current US mice catalog
  capture.
- Mousepad: two current Razer Gigantus V2 SKU pages plus the current size table
  from the overview page.
- Desk: three current IKEA desk examples from the current US desks listing.
- Percentages and ownership figures keep the source wording and limitations.

## Raw values used

### Phone placement

- CHI 2013 Phoneprioception study:
  - 68% of 650 respondents had their phone on a table or desk when asked.
  - 83% of 693 respondents had placed it on a table or desk within the prior
    24 hours.
- Limitations:
  - 2013 study, not a current representative survey.
  - "table or desk" does not specifically mean "computer desk."

### Smartphone ownership

- Pew Research Center mobile phone ownership chart:
  - 91% of U.S. adults owned a smartphone in the June 18, 2025 row.

### Logitech pointing-device snapshot

Visible first-page prices captured from the current Logitech US mice catalog:

`79.99, 49.99, 39.99, 119.99, 119.99, 22.99, 79.99, 22.99, 79.99, 89.99, 22.99, 99.99, 119.99, 20.00, 22.99, 119.99, 22.99, 44.99`

Computed:

- count: 18
- min: $20.00
- max: $119.99
- mean: $65.55
- median: $64.99

### Mousepad snapshot

Current Razer Gigantus V2 sample:

- sampled SKU prices: $20.00 and $15.00
- sample mean: $17.50
- overview-page size table:
  - Medium: 14.17 x 10.83 in = 153.46 in2
  - Large: 17.72 x 15.73 in = 278.74 in2

Illustrative combined snapshot:

- mouse mean + mousepad mean = $83.05

### Desk sample

Current IKEA sample:

- MICKE, 28 3/4 x 19 5/8 in, $69.99
- KALLAX desk, 43 3/4 x 15 3/8 in, $79.99
- LAGKAPTEN / ALEX desk, 55 1/8 x 23 5/8 in, $239.99

Computed areas:

- MICKE: 564.22 in2
- KALLAX: 672.66 in2
- LAGKAPTEN / ALEX: 1302.33 in2

Computed sample metrics:

- mean surface area: 846.40 in2
- mean price: $129.99
- implied allocation: $0.1536 per in2

Mousepad footprint illustration against the desk sample:

- 153.46 in2 pad:
  - 18.1% of the sample mean desk surface
  - $23.57 surface allocation
- 278.74 in2 pad:
  - 32.9% of the sample mean desk surface
  - $42.81 surface allocation

## Formulas

Mouse mean:

`sum(visible Logitech prices) / 18`

Mousepad mean:

`(20.00 + 15.00) / 2`

Combined illustrative hardware snapshot:

`65.55 + 17.50 = 83.05`

Desk allocation:

`mean_desk_price / mean_desk_area = 129.99 / 846.40 = 0.1536`

Surface allocation:

`mousepad_area * 0.1536`

## README claim mapping

- "Phones are commonly already within reach":
  Phoneprioception + Pew ownership, with limitations stated in footnotes.
- "A July 27, 2026 manufacturer snapshot of 18 visible Logitech catalog entries
  ranged from $20.00 to $119.99 and averaged $65.55":
  Logitech visible-price capture above.
- "Two sampled Razer Gigantus V2 SKU pages were $15.00 and $20.00":
  sampled SKU prices above.
- "Together that small snapshot lands around $83.05":
  combined illustrative hardware snapshot formula.
- "The medium and large Gigantus V2 size table spans about 153-279 square
  inches":
  overview-page size table above.
- "Three current IKEA desk examples averaged 846 square inches and $129.99, or
  about $0.15 per square inch":
  desk sample above.
- "$24-$43 space allocation":
  surface allocation illustration above.

## Limitations

- Logitech sample is one manufacturer page, first-page visible entries only, not
  sales-weighted and not a market average.
- Razer sample is two SKU pages plus a separate overview-page size table.
- IKEA sample is three current listings, not a market average.
- Desk-space allocation is opportunity cost, not cash recovery.
- If the phone was not already occupying that spot, its own footprint should be
  subtracted before claiming recovered space.
