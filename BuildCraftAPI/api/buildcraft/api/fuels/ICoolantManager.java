/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.fuels;

import java.util.Collection;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public interface ICoolantManager {
    ICoolant addCoolant(ICoolant coolant);

    ICoolant addCoolant(Fluid fluid, float degreesCoolingPerMB);

    ISolidCoolant addSolidCoolant(ISolidCoolant solidCoolant);

    ISolidCoolant addSolidCoolant(ItemStack solid, FluidStack fluid, float multiplier);

    Collection<ICoolant> getCoolants();

    Collection<ISolidCoolant> getSolidCoolants();

    ICoolant getCoolant(Fluid fluid);

    ISolidCoolant getSolidCoolant(ItemStack solid);
}
