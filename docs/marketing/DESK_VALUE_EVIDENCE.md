# Desk Value Evidence

Captured on: July 27, 2026

Owner: Codecks maintainers

Next review: before the next public README claim refresh

## Conclusion

The strongest supportable economic claim is:

- estimated 2025 global computer-mouse ASP: **$38**;
- estimated standard mousepad ASP: **$5–$15**;
- mouse-only avoided-new-hardware benchmark: **$38**;
- mouse-plus-standard-pad benchmark: **$43–$53**;
- medium-to-large pad footprint: **153–279 in2**;
- share of a 48×24 to 60×30 work surface: **about 9%–24%**.

Cash and space are separate. Codecks can avoid a future purchase only when the
user would otherwise buy a mouse or pad. It can reclaim gross pad area only
when the phone was already on the work surface.

No public source found in this review provides a global, sales-weighted desk
selling price together with sales-weighted surface dimensions. A dollar value
per square inch would therefore combine unlike samples and imply false
precision. The README intentionally makes no such claim.

## Evidence hierarchy

| Claim | Evidence | Confidence |
| --- | --- | --- |
| U.S. smartphone ownership | Representative Pew survey, n=5,022 | High for U.S. adults |
| Indoor phone placement | Peer-reviewed convenience sample, n=356 | Low outside sampled Melbourne men |
| Mouse ASP | Commercial global market estimate | Medium-low |
| Standard mousepad ASP | Commercial global segment estimate | Medium-low |
| Pad footprint | Current manufacturer dimensions | High for those two sizes |
| Work-surface scale | IBM workplace-design guidance | High as guidance, not a market average |
| Home-workspace scarcity | Peer-reviewed weighted UK survey, n=2,543 | High for surveyed UK home workers |
| Phone-proximity tradeoff | Peer-reviewed within-person lab experiment, n=22 | Moderate; small sample |

## Current research

### Smartphone ownership

Pew Research Center's Mobile Fact Sheet reports that 91% of U.S. adults owned
a smartphone. It uses a representative address-based survey of 5,022 adults
conducted from February 5 through June 18, 2025.

Source:
`https://www.pewresearch.org/internet/fact-sheet/mobile/`

This supports availability of the required hardware in the United States. It
does not say where a phone is placed or whether its owner uses a Mac.

### Phone placement

Zeleke et al. surveyed 356 men aged 18–72 in Melbourne. When indoors at home or
work and not using the phone, 54.0% said they often or very often kept it on a
table or desk. Data were collected from October 2018 through February 2019 and
published in 2022.

Source:
`https://doi.org/10.1371/journal.pone.0269457`

The authors explicitly say the convenience sample is not representative of all
Australian men. It excludes women and does not distinguish a computer desk
from other tables. The README therefore does not turn 54% into a global
market-size claim.

### Home workspace

Felstead's 2026 peer-reviewed analysis uses weighted Skills and Employment
Survey 2024 data. Among 2,543 UK home workers:

- 41.1% had their own office;
- 5.5% shared an office;
- 24.8% used a workstation in a room that was not an office;
- 28.6% worked in spaces intended for other uses.

Thus 58.9% did not have their own home office. This is evidence that desktop
capacity matters for many home workers, not a measurement of Codecks demand.

Source:
`https://orca.cardiff.ac.uk/id/eprint/184858/1/Industrial%20Relations%20Journal%20-%202026%20-%20Felstead%20-%20The%20Spatial%20Anatomy%20of%20Working%20at%20Home%20Concepts%20Measures%20and%20Types%20of.pdf`

### Phone-proximity tradeoff

Heitmayer's 2025 within-participant experiment observed 22 laptop workers for
five hours in each of two conditions. With the phone within reach, median
phone interactions were 18.5 versus 6.5 when it was 1.5 meters away. Total time
spent working did not significantly differ.

Source:
`https://doi.org/10.3389/fcomp.2025.1422244`

Codecks deliberately makes the phone useful and reachable. That could increase
phone interaction or distraction, so the README includes this tradeoff.

## Market price evidence

### Mouse

Dataintelo estimates:

- 2025 global computer-mouse market: $3.8 billion;
- 2025 market-wide ASP: about $38;
- top ten best-selling gaming-mouse ASP: about $94.

Source:
`https://dataintelo.com/report/computer-mouse-market`

The $38 figure is broader and more relevant than the previous one-brand,
18-listing mean of $65.55. It remains a commercial estimate: the public page
does not disclose the underlying transaction panel or sales weights. The
README labels it an estimate.

### Mousepad

Dataintelo estimates standard mousepads sold for $5–$15 on average in 2025.
Standard pads represented 17.0% of estimated market revenue; gaming and other
premium categories cost more.

Source:
`https://dataintelo.com/report/mousepad-market`

Public market-size estimates for mousepads vary dramatically. For example,
Research and Markets gives a $126.71 million 2025 global market estimate while
Dataintelo gives $1.37 billion. Neither public page supplies enough unit data
to reproduce a sales-weighted market-wide ASP.

Cross-check:
`https://www.researchandmarkets.com/report/mouse-pad-market`

The README therefore uses the $5–$15 standard-pad range as a conservative
replacement benchmark, not as the average of every pad sold worldwide.

## Space calculation

Current Razer Gigantus V2 dimensions:

- Medium: 14.17 × 10.83 in = 153.46 in2;
- Large: 17.72 × 15.73 in = 278.74 in2.

Source:
`https://www.razer.com/gaming-mouse-mats/razer-gigantus-v2`

IBM individual-workplace guidance:

- minimum work surface: 48 × 24 in = 1,152 in2;
- recommended work surface: 60 × 30 in = 1,800 in2.

Source:
`https://www.ibm.com/design/workplace/space-types/individual/approach/`

Pad share:

- 153.46 / 1,800 = 8.5%;
- 153.46 / 1,152 = 13.3%;
- 278.74 / 1,800 = 15.5%;
- 278.74 / 1,152 = 24.2%.

The README rounds the full range to 9%–24%. These are examples of medium and
large pad footprint against design-guidance surfaces, not market averages.

## Hardware benchmark formula

Low:

`$38 mouse ASP + $5 standard pad = $43`

High:

`$38 mouse ASP + $15 standard pad = $53`

Excluded:

- tax and shipping;
- the value of a phone or Mac, because Codecks requires both;
- sunk value of a mouse or pad already owned;
- premium gaming or ergonomic hardware;
- labor, setup time, battery use, and learning cost;
- a dollar value for desk surface.

## Rejected claims

- **"$83.05 average savings":** based on one manufacturer's visible catalog
  and two premium-brand pad pages, not the market.
- **"$0.15 per desk square inch":** based on three IKEA products with
  unweighted price and area means.
- **"$24–$43 of desk value reclaimed":** multiplication of the unsupported
  per-inch figure.
- **"Most phones are on computer desks":** no current representative global
  placement study supports this.

## Reproduction

Structured inputs:
`docs/marketing/desk_value_snapshot.csv`

Arithmetic check:

```bash
python3 tools/verify_desk_value_snapshot.py
```
