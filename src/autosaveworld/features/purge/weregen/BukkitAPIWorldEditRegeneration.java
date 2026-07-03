/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package autosaveworld.features.purge.weregen;

import java.util.Iterator;
import java.util.LinkedList;

import org.bukkit.Material;
import org.bukkit.World;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

import autosaveworld.core.logging.MessageLogger;
import autosaveworld.features.purge.weregen.UtilClasses.BlockToPlaceBack;
import autosaveworld.features.purge.weregen.UtilClasses.ItemSpawnListener;
import autosaveworld.features.purge.weregen.WorldEditRegeneration.WorldEditRegenrationInterface;
import autosaveworld.utils.BukkitUtils;

public class BukkitAPIWorldEditRegeneration implements WorldEditRegenrationInterface {

	private ItemSpawnListener itemremover = new ItemSpawnListener();

	@SuppressWarnings("removal")
	@Override
	public void regenerateRegion(World world, BlockVector3 minpoint, BlockVector3 maxpoint) {
		com.sk89q.worldedit.world.World wew = BukkitAdapter.adapt(world);
		int maxy = world.getMaxHeight();
		Region region = new CuboidRegion(wew, minpoint, maxpoint);
		LinkedList<BlockToPlaceBack> placeBackQueue = new LinkedList<BlockToPlaceBack>();

		// register listener that will prevent trash items from spawning
		BukkitUtils.registerListener(itemremover);
		try (EditSession es = WorldEdit.getInstance().newEditSessionBuilder().world(wew).maxBlocks(-1).build()) {
			es.setFastMode(true);

			// first save all blocks that are inside affected chunks but outside the region
			for (BlockVector2 chunk : region.getChunks()) {
				BlockVector3 min = BlockVector3.at(chunk.x() * 16, 0, chunk.z() * 16);
				for (int x = 0; x < 16; ++x) {
					for (int y = 0; y < maxy; ++y) {
						for (int z = 0; z < 16; ++z) {
							BlockVector3 pt = min.add(x, y, z);
							if (!region.contains(pt)) {
								placeBackQueue.add(new BlockToPlaceBack(pt, es.getFullBlock(pt)));
							}
						}
					}
				}
			}

			//TODO: Set blocks that has tileentity to air first

			// regenerate all affected chunks
			for (BlockVector2 chunk : region.getChunks()) {
				try {
					world.regenerateChunk(chunk.x(), chunk.z());
				} catch (Exception t) {
					MessageLogger.exception("Unable to regenerate chunk " + chunk.x() + " " + chunk.z(), t);
				}
			}

			// set all blocks that were outside the region back. MULTI_STAGE reorders the
			// changes internally so blocks that depend on their neighbour (torches, doors,
			// redstone, etc.) get placed only after the block they attach to.
			es.setReorderMode(EditSession.ReorderMode.MULTI_STAGE);
			Iterator<BlockToPlaceBack> entryit = placeBackQueue.iterator();
			while (entryit.hasNext()) {
				BlockToPlaceBack blockToPlaceBack = entryit.next();
				BaseBlock block = blockToPlaceBack.getBlock();
				BlockVector3 pt = blockToPlaceBack.getPosition();
				try {
					// set block to air to fix one really weird problem
					world.getBlockAt(pt.x(), pt.y(), pt.z()).setType(Material.AIR);
					// set block back if it is not air
					if (block.getBlockType() != BlockTypes.AIR) {
						es.setBlock(pt, block);
					}
				} catch (Exception t) {
					MessageLogger.exception("Unable to place back block " + pt.x() + " " + pt.y() + " " + pt.z(), t);
				} finally {
					entryit.remove();
				}
			}
		} finally {
			// unregister listener that prevents item drop
			BukkitUtils.unregisterListener(itemremover);
		}
	}

}
