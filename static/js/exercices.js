export const EXERCICES = {
    pompes: {
        nom: "Pompes",
        stage_initial: "haut",
        angle_flechi: 95,
        angle_etendu: 155,
        haut_quand_flechi: false,
        menton: false,
        articulation: "coude",
    },
    dips: {
        nom: "Dips",
        stage_initial: "haut",
        angle_flechi: 95,
        angle_etendu: 155,
        haut_quand_flechi: false,
        menton: false,
        articulation: "coude",
    },
    tractions: {
        nom: "Tractions",
        stage_initial: "bas",
        angle_flechi: 120,
        angle_etendu: 160,
        haut_quand_flechi: true,
        menton: true,
        articulation: "coude",
    },
    squats: {
        nom: "Squats",
        stage_initial: "haut",
        angle_flechi: 85,
        angle_etendu: 165,
        haut_quand_flechi: false,
        menton: false,
        articulation: "genou",
    },
    plank: {
        nom: "Gainage",
        stage_initial: "bas",
        angle_flechi: 160,
        angle_etendu: 175,
        haut_quand_flechi: false,
        menton: false,
        articulation: "coude",
        temps: true,
    },
};

const SVG = "xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'";

export const EXO_META = {
    pompes: {
        icon: "<svg " + SVG + "><path d='m6.5 6.5 11 11'/><path d='m21 21-1-1'/><path d='m3 3 1 1'/><path d='m18 22 4-4'/><path d='m2 6 4-4'/><path d='m3 10 7-7'/><path d='m14 21 7-7'/></svg>",
        desc: "Push-ups classiques",
    },
    dips: {
        icon: "<svg " + SVG + "><path d='m21 16-4 4-4-4'/><path d='M17 20V4'/><path d='m3 8 4-4 4 4'/><path d='M7 4v16'/></svg>",
        desc: "Dips aux barres",
    },
    tractions: {
        icon: "<svg " + SVG + "><path d='m8 6 4-4 4 4'/><path d='M12 2v20'/></svg>",
        desc: "Chin-ups / pull-ups",
    },
    squats: {
        icon: "<svg " + SVG + "><circle cx='12' cy='5' r='1'/><path d='m9 20 3-6 3 6'/><path d='m6 8 6 2 6-2'/><path d='M12 10v4'/></svg>",
        desc: "Squats libres",
    },
    plank: {
        icon: "<svg " + SVG + "><path d='m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z'/><path d='m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65'/><path d='m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65'/></svg>",
        desc: "Gainage — tenue chronométrée",
    },
};