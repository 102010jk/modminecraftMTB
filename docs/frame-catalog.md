# Frame catalogue

Six pixel interpretations of real full-suspension frames. Tube profiles, linkage layouts and shock placement differ. Riding parameters remain shared; the brand linkage animation follows the requested rear travel without changing the physics solver.

| Frame | Model details | Manufacturer reference |
|---|---|---|
| Santa Cruz Nomad | Stepped top tube, curved down tube, low horizontal shock, two moving links and a rigid rear triangle | [Nomad / VPP](https://www.santacruzbicycles.com/collections/nomad) |
| Canyon Torque AL | Angular alloy tubes, diagonal shock, short upper rocker and Horst rear joint | [Torque AL launch](https://media-centre.canyon.com/en-INT/264505-canyon-unveils-all-new-torque-al-long-travel-bruiser/) |
| Cube Stereo ONE77 | Lower bowed top tube, substantial down tube, upper rocker and articulated seatstays | [Stereo ONE range](https://file.cube.eu/azwesc1xfg346/media/24/4f/81/1689945513/CUBE_Press_release_Stereo_ONE_EN.pdf) |
| Commencal META SX V5 | Straight alloy outline, two moving links, rigid rear triangle and lower-link shock mount | [VCS construction](https://www.commencal.com/fr/landing-vcs.html) |
| YT Capra | Sloping top tube, angled shock, paired upper rocker and Horst rear joint | [Capra Core 4](https://www.yt-industries.com/en-us/Bikes/Enduro-Capra/CORE-4/) |
| Specialized Stumpjumper 15 | Slim top tube, forward down-tube bend, shock below the top tube and short rocker behind the seat tube | [Stumpjumper 15 frameset](https://www.specialized.com/us/en/s-works-stumpjumper-15-frameset-fox-float-genie-factory/p/4221408) |

The model details are visual interpretations of manufacturer profiles and layouts, not dimensionally exact CAD. `tools/frame_profiles.json` is authoritative for the six brand outlines and pivots. `tools/gen_frame_catalog.py` validates four-bar closure across 160 mm travel and generates the literal cube geometry plus `FrameLayouts.java`. Run it before `gen_custom_assets.py` and `gen_enduro_texture.py`; decal anchors and UV textures then follow the model. The enduro UV atlas is now 128×512 to give the separate rear assemblies non-overlapping regions. The existing material painter is unchanged.

`EnduroLinkage` closes the four-bar with circle intersections and a rest-pose branch choice, then maps requested axle rise through a precomputed monotonic lookup. Shock eyes use those same moving points. Shock body dimensions stay constant during compression; the shaft telescopes and the spring compresses. The stock common rear assembly is visible only for the three generic enduro shapes. Brand render poses and scales are restored after drawing, including in workshop/item renders.

Dirt frames keep a separate three-shape catalogue. Their top tubes connect directly to the seat tube; the straight variant is a sloping straight tube. The saddle now has a shorter nose, rear, base and rails, on the existing low inserted post and coaxial clamp. The seatstay bridge follows the actual convergence of the stays.
